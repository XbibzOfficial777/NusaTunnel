/* Switch glass + Segmented control glass. */

export function Switch({
  on, disabled, onChange, title,
}: {
  on: boolean;
  disabled?: boolean;
  onChange: () => void;
  title?: string;
}) {
  return (
    <button
      className={`switch${on ? " on" : ""}${disabled ? " disabled" : ""}`}
      onClick={onChange}
      title={title}
      role="switch"
      aria-checked={on}
      aria-disabled={disabled}
    >
      <span className="knob" />
    </button>
  );
}

export function Segmented<T extends string>({
  value, options, onChange,
}: {
  value: T;
  options: Array<{ val: T; label: string }>;
  onChange: (v: T) => void;
}) {
  const idx = Math.max(0, options.findIndex((o) => o.val === value));
  return (
    <div className="segmented">
      {options.map((o) => (
        <button
          key={o.val}
          className={o.val === value ? "active" : ""}
          onClick={() => onChange(o.val)}
        >
          {o.val === value && <span className="seg-fill" />}
          {o.label}
        </button>
      ))}
      <span className="seg-glider" style={{ transform: `translateX(${idx * 100}%)` }} />
    </div>
  );
}
