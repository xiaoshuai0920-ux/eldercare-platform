export function activateFocusedButton(event) {
  // #ifdef H5
  const key = event?.key
  if ((key === 'Enter' || key === ' ' || key === 'Spacebar') && !event.repeat && event.target?.tagName === 'UNI-BUTTON') {
    event.preventDefault()
    event.target.click()
  }
  // #endif
}
