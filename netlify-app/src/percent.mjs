const formatter = new Intl.NumberFormat('es-PE', { maximumFractionDigits: 20 });
export const formatPercent = value => formatter.format(value);
