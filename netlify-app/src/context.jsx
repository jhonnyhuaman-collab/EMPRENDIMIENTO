import { createContext, useContext } from 'react';
export const AppContext = createContext(null);
export const useApp = () => useContext(AppContext);
export const LABELS = { PRACTICA: 'Práctica', EXAMEN: 'Examen', LABORATORIO: 'Laboratorio', PROYECTO: 'Proyecto', TAREA: 'Tarea' };
