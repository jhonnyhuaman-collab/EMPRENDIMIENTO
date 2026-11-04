import React, { useEffect, useId, useRef, useState } from 'react';
import { X, LoaderCircle, Download, ArrowLeft } from 'lucide-react';
import { Link } from 'react-router-dom';

export function Button({ icon: Icon, children, variant = 'primary', busy, ...props }) {
  return <button className={`button ${variant}`} {...props} disabled={props.disabled || busy}>
    {busy ? <LoaderCircle className="spin" size={18} aria-hidden="true" /> : Icon && <Icon size={18} aria-hidden="true" />}{children}
  </button>;
}
export function IconButton({ icon: Icon, label, ...props }) {
  return <button className="icon-button" type="button" title={label} aria-label={label} {...props}><Icon size={18} aria-hidden="true" /></button>;
}
export function Field({ label, children, hint, ...props }) {
  const id = useId();
  const identify = element => {
    if (!React.isValidElement(element)) return element;
    if (['input', 'select', 'textarea'].includes(element.type)) return React.cloneElement(element, { id, ...props });
    return element.props.children ? React.cloneElement(element, {}, React.Children.map(element.props.children, identify)) : element;
  };
  return <div className="field"><label htmlFor={id}>{label}</label>{children ? identify(children) : <input id={id} {...props} />}{hint && <small>{hint}</small>}</div>;
}
export function Modal({ title, onClose, children }) {
  const dialog = useRef();
  const titleId = useId();
  useEffect(() => { dialog.current.showModal(); return () => dialog.current?.close(); }, []);
  return <dialog ref={dialog} className="modal" onCancel={e => { e.preventDefault(); onClose(); }} aria-labelledby={titleId}
    onClick={e => { if (e.target === e.currentTarget) { const r = e.currentTarget.getBoundingClientRect(); if (e.clientX < r.left || e.clientX > r.right || e.clientY < r.top || e.clientY > r.bottom) onClose(); } }}>
    <div className="modal-head"><h2 id={titleId}>{title}</h2><IconButton icon={X} label="Cerrar" onClick={onClose} /></div>{children}
  </dialog>;
}
export function Empty({ icon: Icon, title, children, action }) {
  return <div className="empty"><span className="empty-icon"><Icon size={28} /></span><h3>{title}</h3>{children && <p>{children}</p>}{action}</div>;
}
export function PageTitle({ eyebrow, title, description, action }) {
  return <header className="page-title"><div>{eyebrow && <span className="eyebrow">{eyebrow}</span>}<h1>{title}</h1>{description && <p>{description}</p>}</div>{action}</header>;
}
export function ErrorMessage({ message }) { return message ? <div className="alert error" role="alert">{message}</div> : null; }
export function Back({ to, children }) { return <Link className="back" to={to}><ArrowLeft size={16} />{children}</Link>; }
export function InstallButton() {
  const [prompt, setPrompt] = useState(null);
  useEffect(() => {
    const handler = event => { if (!matchMedia('(display-mode: standalone)').matches && !navigator.standalone) { event.preventDefault(); setPrompt(event); } };
    const installed = () => setPrompt(null);
    window.addEventListener('beforeinstallprompt', handler); window.addEventListener('appinstalled', installed);
    return () => { window.removeEventListener('beforeinstallprompt', handler); window.removeEventListener('appinstalled', installed); };
  }, []);
  if (!prompt) return null;
  return <Button variant="secondary install" icon={Download} onClick={async () => { const current = prompt; setPrompt(null); try { await current.prompt(); } catch { /* La instalacion puede cancelarse. */ } }}>Instalar aplicación</Button>;
}
