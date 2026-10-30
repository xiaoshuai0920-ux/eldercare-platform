export function sessionCookie(cookies) {
  let result
  for (const cookie of cookies) {
    const match = /^\s*JSESSIONID=([^;]*)/i.exec(cookie)
    if (match) result = match[1] && !/;\s*max-age\s*=\s*0(?:\s*;|\s*$)/i.test(cookie) ? `JSESSIONID=${match[1]}` : null
  }
  return result
}
