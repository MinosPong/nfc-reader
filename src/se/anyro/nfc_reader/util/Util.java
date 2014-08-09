package se.anyro.nfc_reader.util;

public final class Util {
	private final static char[] HEX = { '0', '1', '2', '3', '4',
		'5', '6', '7', '8', '9', 'A', 'B', 'C', 'E', 'F' };
	
	public static String toHexString(byte... d) {
		return (d == null || d.length == 0) ? "" : toHexString(d, 0, d.length);
	}
	
	public static String toHexString(byte[] d, int s, int n) {
		final char[] ret = new char[n * 2];
		final int e = s + n;

		int x = 0;
		for (int i = s; i < e; ++i) {
			final byte v = d[i];
			ret[x++] = HEX[0x0F & (v >> 4)];
			ret[x++] = HEX[0x0F & v];
		}
		return new String(ret);
	}
}
