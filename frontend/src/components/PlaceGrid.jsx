import PlaceCard from './PlaceCard.jsx'

function SkeletonCard() {
  return (
    <div className="place-card skeleton-card" aria-hidden="true">
      <div className="skeleton skeleton--visual" />
      <div className="place-card__body">
        <div className="skeleton skeleton--title" />
        <div className="skeleton skeleton--line" />
        <div className="skeleton skeleton--short" />
      </div>
    </div>
  )
}

export default function PlaceGrid({ loading, places, onSelect, onAddToPlan, isInPlan }) {
  return (
    <div className="place-grid">
      {loading
        ? Array.from({ length: 8 }, (_, index) => <SkeletonCard key={index} />)
        : places?.map((place) => (
          <PlaceCard
            key={place.id}
            place={place}
            onSelect={onSelect}
            onAddToPlan={onAddToPlan}
            isInPlan={isInPlan(place.id)}
          />
        ))}
    </div>
  )
}
