package org.missionweaveprotocol.sdk;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** RFC 3339 instant with arbitrary fractional precision and no rounding. */
public record ExactInstant(long epochSecond, String fractionalDigits)
    implements Comparable<ExactInstant> {
  private static final Pattern RFC3339 =
      Pattern.compile(
          "^(?<year>[0-9]{4})-(?<month>[0-9]{2})-(?<day>[0-9]{2})"
              + "[Tt](?<hour>[0-9]{2}):(?<minute>[0-9]{2}):(?<second>[0-9]{2})"
              + "(?:\\.(?<fraction>[0-9]+))?(?<offset>[Zz]|[+-][0-9]{2}:[0-9]{2})$");

  public ExactInstant {
    Objects.requireNonNull(fractionalDigits, "fractionalDigits");
    if (!fractionalDigits.chars().allMatch(character -> character >= '0' && character <= '9')
        || fractionalDigits.endsWith("0")) {
      throw new IllegalArgumentException(
          "fractionalDigits must be empty or normalized without trailing zeroes");
    }
  }

  /** Parse the MissionWeaveProtocol timestamp profile without truncating fractional digits. */
  public static ExactInstant parse(String text) {
    Objects.requireNonNull(text, "text");
    Matcher match = RFC3339.matcher(text);
    if (!match.matches()) {
      throw new IllegalArgumentException("not an RFC 3339 timestamp");
    }

    int year = integer(match, "year");
    int month = integer(match, "month");
    int day = integer(match, "day");
    int hour = integer(match, "hour");
    int minute = integer(match, "minute");
    int second = integer(match, "second");
    if (year == 0) {
      throw new IllegalArgumentException("year 0000 is not supported");
    }
    if (hour > 23 || minute > 59 || second > 59) {
      throw new IllegalArgumentException("timestamp time is outside protocol bounds");
    }

    final long epochDay;
    try {
      epochDay = LocalDate.of(year, month, day).toEpochDay();
    } catch (DateTimeException error) {
      throw new IllegalArgumentException("invalid Gregorian date", error);
    }

    String offset = match.group("offset");
    if (offset.equals("-00:00")) {
      throw new IllegalArgumentException("unknown local offset -00:00 is not an instant");
    }
    int offsetSeconds = 0;
    if (!offset.equals("Z") && !offset.equals("z")) {
      int offsetHour = Integer.parseInt(offset.substring(1, 3));
      int offsetMinute = Integer.parseInt(offset.substring(4, 6));
      if (offsetHour > 23 || offsetMinute > 59) {
        throw new IllegalArgumentException("numeric offset is outside RFC 3339 bounds");
      }
      int direction = offset.charAt(0) == '+' ? 1 : -1;
      offsetSeconds = direction * (offsetHour * 3600 + offsetMinute * 60);
    }

    String fraction = match.group("fraction");
    fraction = fraction == null ? "" : fraction.replaceFirst("0+$", "");
    long localSecond = epochDay * 86400L + hour * 3600L + minute * 60L + second;
    return new ExactInstant(localSecond - offsetSeconds, fraction);
  }

  @Override
  public int compareTo(ExactInstant other) {
    int seconds = Long.compare(epochSecond, Objects.requireNonNull(other, "other").epochSecond);
    if (seconds != 0) {
      return seconds;
    }
    int width = Math.max(fractionalDigits.length(), other.fractionalDigits.length());
    return fractionalDigits
        .concat("0".repeat(width - fractionalDigits.length()))
        .compareTo(
            other.fractionalDigits.concat("0".repeat(width - other.fractionalDigits.length())));
  }

  private static int integer(Matcher match, String name) {
    return Integer.parseInt(match.group(name));
  }
}
