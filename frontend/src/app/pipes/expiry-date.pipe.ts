import { Pipe, PipeTransform } from '@angular/core';

/**
 * Calculates a human-readable relative time string (e.g., "in 5 days", "2 months ago")
 * compared to the current time.
 * @param timestamp The timestamp (in milliseconds since epoch) to compare.
 * @returns A relative time string.
 */
function getRelativeTimeString(timestamp: number): string {
  const now = Date.now();
  const diffMs = timestamp - now;
  const rtf = new Intl.RelativeTimeFormat('en', { numeric: 'auto' });

  const seconds = Math.round(diffMs / 1000);
  const minutes = Math.round(seconds / 60);
  const hours = Math.round(minutes / 60);
  const days = Math.round(hours / 24);
  const weeks = Math.round(days / 7);
  const months = Math.round(days / 30.44); // Approximate
  const years = Math.round(days / 365.25); // Approximate

  if (Math.abs(years) >= 1) {
    return rtf.format(years, 'year');
  } else if (Math.abs(months) >= 1) {
    return rtf.format(months, 'month');
  } else if (Math.abs(weeks) >= 1) {
    return rtf.format(weeks, 'week');
  } else if (Math.abs(days) >= 1) {
    return rtf.format(days, 'day');
  } else if (Math.abs(hours) >= 1) {
    return rtf.format(hours, 'hour');
  } else if (Math.abs(minutes) >= 1) {
    return rtf.format(minutes, 'minute');
  } else {
    return rtf.format(seconds, 'second');
  }
}


@Pipe({
  name: 'expiryDate',
  standalone: true
})
export class ExpiryDatePipe implements PipeTransform {

  transform(value: string | undefined): string {
    if (!value) {
      return 'N/A';
    }
    if (value === "-1") {
      return '-Never-';
    }

    // The timestamp is in milliseconds since epoch, as a string.
    try {
      const timestamp = parseInt(value, 10);
      if (isNaN(timestamp) || timestamp <= 0) {
        // Handle cases where parsing fails or the timestamp is invalid (e.g., 0)
        console.warn(`ExpiryDatePipe: Could not parse timestamp or invalid value: ${value}`);
        return `Invalid/Unknown (${value})`;
      }
      return `${(new Date(timestamp)).toISOString()} (${getRelativeTimeString(timestamp)})`;
    } catch (e) {
      console.error(`ExpiryDatePipe: Error formatting timestamp: ${value}`, e);
      return `Error (${value})`;
    }
  }
}
