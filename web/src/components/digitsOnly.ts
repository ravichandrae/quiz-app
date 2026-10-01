/** Keeps only digits, so "98765 43210" and "98765-43210" both work. */
export function digitsOnly(value: string): string {
  return value.replace(/\D/g, '')
}
