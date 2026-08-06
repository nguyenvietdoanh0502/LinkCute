import { Check, ChevronDown, Search } from 'lucide-react'
import { useEffect, useId, useMemo, useRef, useState } from 'react'

function normalizeText(value) {
  return String(value || '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLocaleLowerCase('vi')
}

export default function SelectDropdown({
  value = '',
  options = [],
  onChange,
  icon = null,
  placeholder = 'Chọn một mục',
  ariaLabel = 'Chọn một mục',
  searchable = false,
  searchPlaceholder = 'Tìm trong danh sách…',
  className = '',
}) {
  const rootRef = useRef(null)
  const triggerRef = useRef(null)
  const searchRef = useRef(null)
  const listboxId = useId()
  const [isOpen, setIsOpen] = useState(false)
  const [query, setQuery] = useState('')
  const [activeIndex, setActiveIndex] = useState(0)

  const selectedOption = options.find((option) => option.value === value)
  const filteredOptions = useMemo(() => {
    const normalizedQuery = normalizeText(query.trim())
    if (!normalizedQuery) return options
    return options.filter((option) => normalizeText(option.label).includes(normalizedQuery))
  }, [options, query])

  const closeMenu = (restoreFocus = false) => {
    setIsOpen(false)
    setQuery('')
    if (restoreFocus) window.requestAnimationFrame(() => triggerRef.current?.focus())
  }

  const openMenu = () => {
    const selectedIndex = options.findIndex((option) => option.value === value)
    setActiveIndex(Math.max(0, selectedIndex))
    setIsOpen(true)
  }

  const chooseOption = (option) => {
    onChange(option.value)
    closeMenu(true)
  }

  useEffect(() => {
    if (!isOpen) return undefined

    const handleOutsidePointer = (event) => {
      if (!rootRef.current?.contains(event.target)) closeMenu()
    }

    document.addEventListener('pointerdown', handleOutsidePointer)
    return () => document.removeEventListener('pointerdown', handleOutsidePointer)
  }, [isOpen])

  useEffect(() => {
    if (!isOpen || !searchable) return undefined
    const frame = window.requestAnimationFrame(() => searchRef.current?.focus())
    return () => window.cancelAnimationFrame(frame)
  }, [isOpen, searchable])

  useEffect(() => {
    if (isOpen) setActiveIndex(0)
  }, [query])

  const handleKeyDown = (event) => {
    if (!isOpen) {
      if (['ArrowDown', 'ArrowUp', 'Enter', ' '].includes(event.key)) {
        event.preventDefault()
        openMenu()
      }
      return
    }

    if (event.key === 'Escape') {
      event.preventDefault()
      closeMenu(true)
    } else if (event.key === 'ArrowDown') {
      event.preventDefault()
      setActiveIndex((current) => Math.min(current + 1, filteredOptions.length - 1))
    } else if (event.key === 'ArrowUp') {
      event.preventDefault()
      setActiveIndex((current) => Math.max(current - 1, 0))
    } else if (event.key === 'Home') {
      event.preventDefault()
      setActiveIndex(0)
    } else if (event.key === 'End') {
      event.preventDefault()
      setActiveIndex(Math.max(0, filteredOptions.length - 1))
    } else if (event.key === 'Enter' && filteredOptions[activeIndex]) {
      event.preventDefault()
      chooseOption(filteredOptions[activeIndex])
    } else if (event.key === 'Tab') {
      closeMenu()
    }
  }

  return (
    <div
      ref={rootRef}
      className={`custom-select ${isOpen ? 'custom-select--open' : ''} ${className}`.trim()}
      onKeyDown={handleKeyDown}
    >
      <button
        ref={triggerRef}
        className="custom-select__trigger"
        type="button"
        aria-label={ariaLabel}
        aria-haspopup="listbox"
        aria-expanded={isOpen}
        aria-controls={listboxId}
        onClick={() => (isOpen ? closeMenu() : openMenu())}
      >
        {icon && <span className="custom-select__leading">{icon}</span>}
        <span className={`custom-select__value ${selectedOption ? '' : 'custom-select__placeholder'}`}>
          {selectedOption?.label || placeholder}
        </span>
        {selectedOption?.count != null && (
          <span className="custom-select__count">{selectedOption.count.toLocaleString('vi-VN')}</span>
        )}
        <ChevronDown className="custom-select__chevron" size={15} />
      </button>

      {isOpen && (
        <div className="custom-select__popover">
          {searchable && (
            <label className="custom-select__search">
              <Search size={15} />
              <input
                ref={searchRef}
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder={searchPlaceholder}
                aria-label={searchPlaceholder}
              />
            </label>
          )}

          <div id={listboxId} className="custom-select__options" role="listbox" aria-label={ariaLabel}>
            {filteredOptions.map((option, index) => {
              const selected = option.value === value
              return (
                <button
                  key={option.value || '__all__'}
                  className={`custom-select__option ${selected ? 'selected' : ''} ${index === activeIndex ? 'active' : ''}`}
                  type="button"
                  role="option"
                  aria-selected={selected}
                  onMouseEnter={() => setActiveIndex(index)}
                  onClick={() => chooseOption(option)}
                >
                  <span className="custom-select__option-check">{selected && <Check size={14} />}</span>
                  <span>{option.label}</span>
                  {option.count != null && <small>{option.count.toLocaleString('vi-VN')}</small>}
                </button>
              )
            })}

            {filteredOptions.length === 0 && (
              <div className="custom-select__empty">Không tìm thấy lựa chọn phù hợp</div>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
