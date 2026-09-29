import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = { title: "Renda Fixa Radar", description: "Compare renda fixa com dados do Banco Central" };
export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="pt-BR"><body>{children}</body></html>;
}
