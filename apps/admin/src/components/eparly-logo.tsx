import Image from "next/image";
import logo from "../../public/eparly-logo.png";

/** Logo partagé avec l'application mobile (apps/mobile/assets/images/eparly_logo.png). */
export function EparlyLogo({ width = 96 }: { width?: number }) {
  return <Image src={logo} alt="Eparly" width={width} height={Math.round((width * 1125) / 2000)} priority />;
}
