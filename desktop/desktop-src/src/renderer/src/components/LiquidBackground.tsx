/* Latar "liquid" — aurora blob bergerak + layer noise halus.
   Murni CSS animation, tanpa canvas (ringan & stabil). */
export function LiquidBackground() {
  return (
    <div className="liquid-bg" aria-hidden="true">
      <div className="blob b1" />
      <div className="blob b2" />
      <div className="blob b3" />
      <div className="blob b4" />
      <div className="bg-vignette" />
      <div className="bg-grain" />
    </div>
  );
}
