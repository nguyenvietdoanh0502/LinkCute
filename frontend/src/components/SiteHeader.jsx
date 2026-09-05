// Presentational chrome: brand, tab navigation, and a slot for the dynamic
// launcher cluster (itinerary / chat / friends / theme / account), which stays
// in App.jsx because it reads app state and the wiring test regexes App source.
export default function SiteHeader({ actions, activeTab = 'list', onNavigate }) {
  const tabs = [
    { key: 'map', label: 'Bản đồ' },
    { key: 'categories', label: 'Danh mục' },
    { key: 'about', label: 'Về LinkCute' },
  ]

  return (
    <header className="site-header">
      <a className="brand" href="#top" aria-label="LinkCute - Trang chủ">
        <span className="brand__mark">L</span>
        <span>link<span>cute</span></span>
      </a>

      <nav className="desktop-nav" aria-label="Điều hướng chính">
        {tabs.map((tab) => (
          <button
            key={tab.key}
            type="button"
            className={`site-tab${activeTab === tab.key ? ' is-active' : ''}`}
            aria-current={activeTab === tab.key ? 'page' : undefined}
            onClick={() => onNavigate?.(tab.key)}
          >
            {tab.label}
          </button>
        ))}
      </nav>

      <div className="header-actions">{actions}</div>
    </header>
  )
}
