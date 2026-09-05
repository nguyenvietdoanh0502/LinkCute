export default function AppFooter() {
  return (
    <>
      <section className="about-band" id="about">
        <div className="about-band__mark">LC</div>
        <div><span className="eyebrow eyebrow--light">Made for curious souls</span><h2>Không chỉ tìm một nơi.<br />Hãy tìm một <em>cảm giác.</em></h2></div>
        <p>LinkCute kết nối dữ liệu địa điểm từ backend với một trải nghiệm khám phá nhẹ nhàng, nhanh chóng và gần gũi.</p>
      </section>
      <footer className="site-footer">
        <a className="brand brand--footer" href="#top"><span className="brand__mark">L</span><span>link<span>cute</span></span></a>
        <p>Demo ReactJS sử dụng LinkCute Backend API.</p>
        <a href="https://linkcute.duckdns.org" target="_blank" rel="noreferrer">API production <span className="online-dot" /> Online</a>
      </footer>
    </>
  )
}
