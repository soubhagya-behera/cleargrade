import type { SVGProps } from 'react';

export interface ClearGradeLogoProps extends SVGProps<SVGSVGElement> {
  variant?: 'brand' | 'onDark';
  title?: string;
}

export function ClearGradeLogo({
  variant = 'brand',
  title,
  className,
  ...svgProps
}: ClearGradeLogoProps) {
  const c = variant === 'onDark' ? '#FFFFFF' : '#0D2143';
  return (
    <svg
      viewBox="0 0 64 64"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className={className}
      role={title ? 'img' : undefined}
      aria-hidden={title ? undefined : true}
      aria-label={title}
      {...svgProps}
    >
      {title ? <title>{title}</title> : null}
      <path
        d="M 46.91 10.70 A 26 26 0 1 0 46.91 53.30 L 41.75 45.93 A 17 17 0 1 1 41.75 18.07 Z"
        fill={c}
      />
      <path
        d="M 28.27 53.22 L 10.50 32.00 L 28.27 10.78"
        stroke="#80B4E3"
        strokeWidth="2.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <circle cx="28.27" cy="53.22" r="2.4" fill="#80B4E3" />
      <circle cx="10.50" cy="32.00" r="2.4" fill="#80B4E3" />
      <circle cx="28.27" cy="10.78" r="2.4" fill="#80B4E3" />
      <path
        d="M 58.00 31.00 L 48.00 43.00 L 41.00 36.00"
        stroke="#FEDF17"
        strokeWidth="4.2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}
