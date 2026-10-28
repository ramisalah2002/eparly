/// <reference types="expo/types" />

declare module '*.module.css' {
  const classes: { readonly [key: string]: string };
  export default classes;
}

declare module '*.css';

declare module '*.svg' {
  const content: any;
  export default content;
}
