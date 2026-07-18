package org.missionweaveprotocol.sdk;

import java.math.BigInteger;

/** Strict Edwards25519 decoding required before invoking a general crypto provider. */
final class Ed25519Point {
  static final BigInteger ORDER =
      BigInteger.TWO.pow(252).add(new BigInteger("27742317777372353535851937790883648493"));

  private static final BigInteger FIELD = BigInteger.TWO.pow(255).subtract(BigInteger.valueOf(19));
  private static final BigInteger D =
      BigInteger.valueOf(-121665).multiply(BigInteger.valueOf(121666).modInverse(FIELD)).mod(FIELD);
  private static final BigInteger SQRT_M1 =
      BigInteger.TWO.modPow(FIELD.subtract(BigInteger.ONE).divide(BigInteger.valueOf(4)), FIELD);
  private static final Point IDENTITY =
      new Point(BigInteger.ZERO, BigInteger.ONE, BigInteger.ONE, BigInteger.ZERO);

  private Ed25519Point() {}

  static void validate(byte[] encoded, boolean allowIdentity, String label) {
    if (encoded.length != 32) {
      throw new IllegalArgumentException(label + " does not encode a 32-byte Ed25519 point");
    }
    int xSign = (encoded[31] >>> 7) & 1;
    byte[] yBytes = encoded.clone();
    yBytes[31] &= 0x7f;
    BigInteger y = littleEndian(yBytes);
    if (y.compareTo(FIELD) >= 0) {
      throw new IllegalArgumentException(label + " is not a canonical Ed25519 point encoding");
    }

    BigInteger ySquared = y.multiply(y).mod(FIELD);
    BigInteger numerator = ySquared.subtract(BigInteger.ONE).mod(FIELD);
    BigInteger denominator = D.multiply(ySquared).add(BigInteger.ONE).mod(FIELD);
    BigInteger xSquared = numerator.multiply(denominator.modInverse(FIELD)).mod(FIELD);
    BigInteger x =
        xSquared.modPow(FIELD.add(BigInteger.valueOf(3)).divide(BigInteger.valueOf(8)), FIELD);
    if (!x.multiply(x).subtract(xSquared).mod(FIELD).equals(BigInteger.ZERO)) {
      x = x.multiply(SQRT_M1).mod(FIELD);
    }
    if (!x.multiply(x).subtract(xSquared).mod(FIELD).equals(BigInteger.ZERO)) {
      throw new IllegalArgumentException(label + " does not decode to an Edwards25519 point");
    }
    if (x.equals(BigInteger.ZERO) && xSign == 1) {
      throw new IllegalArgumentException(label + " uses a noncanonical negative-zero encoding");
    }
    if ((x.testBit(0) ? 1 : 0) != xSign) {
      x = FIELD.subtract(x);
    }

    Point point = new Point(x, y, BigInteger.ONE, x.multiply(y).mod(FIELD));
    if (isIdentity(point) && !allowIdentity) {
      throw new IllegalArgumentException(label + " encodes the Ed25519 identity point");
    }
    if (!isIdentity(multiply(point, ORDER))) {
      throw new IllegalArgumentException(label + " is not in the prime-order Ed25519 subgroup");
    }
  }

  static BigInteger littleEndian(byte[] bytes) {
    byte[] bigEndian = new byte[bytes.length + 1];
    for (int index = 0; index < bytes.length; index++) {
      bigEndian[bytes.length - index] = bytes[index];
    }
    return new BigInteger(bigEndian);
  }

  private static Point add(Point left, Point right) {
    BigInteger a = left.y.subtract(left.x).multiply(right.y.subtract(right.x)).mod(FIELD);
    BigInteger b = left.y.add(left.x).multiply(right.y.add(right.x)).mod(FIELD);
    BigInteger c = BigInteger.TWO.multiply(D).multiply(left.t).multiply(right.t).mod(FIELD);
    BigInteger d = BigInteger.TWO.multiply(left.z).multiply(right.z).mod(FIELD);
    BigInteger e = b.subtract(a).mod(FIELD);
    BigInteger f = d.subtract(c).mod(FIELD);
    BigInteger g = d.add(c).mod(FIELD);
    BigInteger h = b.add(a).mod(FIELD);
    return new Point(
        e.multiply(f).mod(FIELD),
        g.multiply(h).mod(FIELD),
        f.multiply(g).mod(FIELD),
        e.multiply(h).mod(FIELD));
  }

  private static Point multiply(Point point, BigInteger scalar) {
    Point result = IDENTITY;
    Point addend = point;
    for (int bit = 0; bit < scalar.bitLength(); bit++) {
      if (scalar.testBit(bit)) {
        result = add(result, addend);
      }
      addend = add(addend, addend);
    }
    return result;
  }

  private static boolean isIdentity(Point point) {
    return point.x.mod(FIELD).equals(BigInteger.ZERO)
        && point.y.subtract(point.z).mod(FIELD).equals(BigInteger.ZERO);
  }

  private record Point(BigInteger x, BigInteger y, BigInteger z, BigInteger t) {}
}
