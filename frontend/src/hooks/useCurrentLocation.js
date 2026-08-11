import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  DEFAULT_LOCATION_MAX_AGE_MS,
  geolocationErrorMessage,
  isFreshLocation,
  requireLocation,
} from '../location/model.js'

const DEFAULT_OPTIONS = {
  enableHighAccuracy: true,
  timeout: 12_000,
  maximumAge: DEFAULT_LOCATION_MAX_AGE_MS,
}

const IDLE_REQUEST_STATE = {
  identity: '',
  status: 'idle',
  error: '',
}

function identityKey(identity) {
  if (identity === null || identity === undefined) return ''
  return String(identity)
}

function locationError(code, message) {
  const error = new Error(message)
  error.code = code
  return error
}

function localizedLocationError(failure) {
  const error = new Error(geolocationErrorMessage(failure))
  error.code = failure?.code
  return error
}

function staleLocationRequestError() {
  const error = new Error('Yêu cầu vị trí đã bị hủy do phiên người dùng thay đổi.')
  error.name = 'AbortError'
  return error
}

export function requestBrowserPosition(geolocation, options) {
  return new Promise((resolve, reject) => {
    try {
      geolocation.getCurrentPosition(resolve, reject, options)
    } catch (error) {
      reject(error)
    }
  })
}

export function isCurrentLocationRequest(request, current) {
  return Boolean(
    current?.mounted
    && request?.generation === current?.generation
    && request?.identity === current?.identity,
  )
}

export function useCurrentLocation(identity = '') {
  const currentIdentity = identityKey(identity)
  const [positionState, setPositionState] = useState(() => ({
    identity: currentIdentity,
    value: null,
  }))
  const [requestState, setRequestState] = useState(() => ({
    ...IDLE_REQUEST_STATE,
    identity: currentIdentity,
  }))
  const positionRef = useRef({ identity: currentIdentity, value: null })
  const pendingRequestRef = useRef(null)
  const mountedRef = useRef(false)
  const generationRef = useRef(0)
  const identityRef = useRef(currentIdentity)
  const resetIdentityRef = useRef(currentIdentity)

  // Updating this ref during render makes an old browser callback stale as soon
  // as React begins rendering another authenticated identity.
  identityRef.current = currentIdentity

  useEffect(() => {
    mountedRef.current = true
    return () => {
      mountedRef.current = false
      generationRef.current += 1
      pendingRequestRef.current = null
    }
  }, [])

  const reset = useCallback(() => {
    const activeIdentity = identityRef.current
    generationRef.current += 1
    pendingRequestRef.current = null
    positionRef.current = { identity: activeIdentity, value: null }

    if (!mountedRef.current) return
    setPositionState({ identity: activeIdentity, value: null })
    setRequestState({
      ...IDLE_REQUEST_STATE,
      identity: activeIdentity,
    })
  }, [])

  useEffect(() => {
    if (resetIdentityRef.current === currentIdentity) return
    resetIdentityRef.current = currentIdentity
    reset()
  }, [currentIdentity, reset])

  const requestLocation = useCallback(({ forceFresh = false } = {}) => {
    const requestIdentity = identityRef.current
    const cachedPosition = positionRef.current.identity === requestIdentity
      ? positionRef.current.value
      : null
    if (!forceFresh && isFreshLocation(cachedPosition)) {
      return Promise.resolve(cachedPosition)
    }

    const pendingRequest = pendingRequestRef.current
    if (pendingRequest?.identity === requestIdentity) return pendingRequest.promise

    let immediateError = null
    if (typeof window !== 'undefined' && window.isSecureContext === false) {
      immediateError = locationError('GEOLOCATION_INSECURE', 'Geolocation requires a secure context.')
    } else if (typeof navigator === 'undefined' || !navigator.geolocation) {
      immediateError = locationError('GEOLOCATION_UNSUPPORTED', 'Geolocation is unavailable.')
    }

    if (immediateError) {
      const failure = localizedLocationError(immediateError)
      if (mountedRef.current && identityRef.current === requestIdentity) {
        setRequestState({ identity: requestIdentity, status: 'error', error: failure.message })
      }
      return Promise.reject(failure)
    }

    const requestContext = {
      identity: requestIdentity,
      generation: generationRef.current,
    }
    const requestIsCurrent = () => isCurrentLocationRequest(requestContext, {
      mounted: mountedRef.current,
      identity: identityRef.current,
      generation: generationRef.current,
    })

    if (requestIsCurrent()) {
      setRequestState({ identity: requestIdentity, status: 'requesting', error: '' })
    }

    const request = requestBrowserPosition(navigator.geolocation, {
      ...DEFAULT_OPTIONS,
      maximumAge: forceFresh ? 0 : DEFAULT_OPTIONS.maximumAge,
    })
      .then((result) => {
        if (!requestIsCurrent()) throw staleLocationRequestError()

        const nextPosition = requireLocation({
          latitude: result.coords.latitude,
          longitude: result.coords.longitude,
          accuracyMeters: result.coords.accuracy,
          capturedAt: result.timestamp || Date.now(),
        })

        if (!requestIsCurrent()) throw staleLocationRequestError()
        positionRef.current = { identity: requestIdentity, value: nextPosition }
        setPositionState({ identity: requestIdentity, value: nextPosition })
        setRequestState({ identity: requestIdentity, status: 'success', error: '' })
        return nextPosition
      })
      .catch((locationFailure) => {
        if (!requestIsCurrent() || locationFailure?.name === 'AbortError') {
          throw staleLocationRequestError()
        }

        const failure = localizedLocationError(locationFailure)
        setRequestState({ identity: requestIdentity, status: 'error', error: failure.message })
        throw failure
      })
      .finally(() => {
        if (pendingRequestRef.current?.promise === request) pendingRequestRef.current = null
      })

    pendingRequestRef.current = { identity: requestIdentity, promise: request }
    return request
  }, [])

  const clearError = useCallback(() => {
    const activeIdentity = identityRef.current
    setRequestState((current) => (
      current.identity === activeIdentity
        ? { ...current, error: '' }
        : { ...IDLE_REQUEST_STATE, identity: activeIdentity }
    ))
  }, [])

  const position = positionState.identity === currentIdentity ? positionState.value : null
  const visibleRequestState = requestState.identity === currentIdentity
    ? requestState
    : { ...IDLE_REQUEST_STATE, identity: currentIdentity }

  return useMemo(() => ({
    position,
    status: visibleRequestState.status,
    error: visibleRequestState.error,
    requestLocation,
    clearError,
    reset,
  }), [
    clearError,
    position,
    requestLocation,
    reset,
    visibleRequestState.error,
    visibleRequestState.status,
  ])
}
