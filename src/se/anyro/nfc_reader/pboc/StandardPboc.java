package se.anyro.nfc_reader.pboc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import android.nfc.tech.IsoDep;
import android.util.Log;
import se.anyro.nfc_reader.SPEC;
import se.anyro.nfc_reader.bean.Application;
import se.anyro.nfc_reader.bean.Card;
import se.anyro.nfc_reader.tech.Iso7816;
import se.anyro.nfc_reader.util.Util;

public abstract class StandardPboc {
	private static Class<?>[][] readers = { {Quickpass.class, } };
	
	public static void readCard(IsoDep tech, Card card)
			throws InstantiationException, IllegalAccessException, IOException {
		final Iso7816.StdTag tag = new Iso7816.StdTag(tech);
		
		tag.connect();
		
		for(final Class<?> g[] : readers) {
			HINT hint = HINT.RESETANDGONEXT;
			
			for(final Class<?> r : g) {
				final StandardPboc reader = (StandardPboc)r.newInstance();
				
				switch(hint) {
				case RESETANDGONEXT:
					Log.i("yz", reader.getClass().getName());
					if(!reader.resetTag(tag)) {
						continue;
					}
					
				case GONEXT:
					hint = reader.readCard(tag, card);
					break;
					
				default:
					break;
				}
				
				if(hint == HINT.STOP)
					break;
			}
		}
		tag.close();
	}
	
	protected boolean resetTag(Iso7816.StdTag tag) throws IOException {
		return tag.selectByID(DFI_MF).isOkey()
				|| tag.selectByName(DFN_PSE).isOkey();
	}
	
	protected boolean selectMainApplication(Iso7816.StdTag tag)
			throws IOException {
		final byte[] aid = getMainApplicationId();
		return ((aid.length == 2) ? tag.selectByID(aid) : tag.selectByName(aid))
				.isOkey();
	}
	
	protected byte[] getMainApplicationId() {
		return DFI_EP;
	}
	
	protected SPEC.CUR getCurrency() {
		return SPEC.CUR.CNY;
	}
	
	protected abstract SPEC.APP getApplicationId();
	
	protected HINT readCard(Iso7816.StdTag tag, Card card) throws IOException {
		/*--------------------------------------------------------------*/
		// select Main Application
		/*--------------------------------------------------------------*/
		if (!selectMainApplication(tag))
			return HINT.GONEXT;

		Iso7816.Response INFO, BALANCE;

		/*--------------------------------------------------------------*/
		// read card info file, binary (21)
		/*--------------------------------------------------------------*/
		INFO = tag.readBinary(SFI_EXTRA);

		/*--------------------------------------------------------------*/
		// read balance
		/*--------------------------------------------------------------*/
		BALANCE = tag.getBalance(true);

		/*--------------------------------------------------------------*/
		// read log file, record (24)
		/*--------------------------------------------------------------*/
		ArrayList<byte[]> LOG = readLog24(tag, SFI_LOG);

		/*--------------------------------------------------------------*/
		// build result
		/*--------------------------------------------------------------*/
		final Application app = createApplication();

		parseBalance(app, BALANCE);

		parseInfo21(app, INFO, 4, true);

		parseLog24(app, LOG);

		configApplication(app);

		card.addApplication(app);
		
		return HINT.STOP;
	}
	
	protected void parseBalance(Application app, Iso7816.Response... data) {
		int amount = 0;
		for (Iso7816.Response rsp : data) {
			if (rsp.isOkey() && rsp.size() >= 4) {
				int n = Util.toInt(rsp.getBytes(), 0, 4);
				if (n > 1000000 || n < -1000000)
					n -= 0x80000000;

				amount += n;
			}
		}

		app.setProperty(SPEC.PROP.BALANCE, (amount / 100.0f));
	}
	
	protected void parseInfo21(Application app, Iso7816.Response data, int dec,
			boolean bigEndian) {
		if (!data.isOkey() || data.size() < 30) {
			return;
		}

		final byte[] d = data.getBytes();
		if (dec < 1 || dec > 10) {
			app.setProperty(SPEC.PROP.SERIAL, Util.toHexString(d, 10, 10));
		} else {
			final int sn = bigEndian ? Util.toIntR(d, 19, dec) : Util.toInt(d,
					20 - dec, dec);

			app.setProperty(SPEC.PROP.SERIAL,
					String.format("%d", 0xFFFFFFFFL & sn));
		}

		if (d[9] != 0)
			app.setProperty(SPEC.PROP.VERSION, String.valueOf(d[9]));

		app.setProperty(SPEC.PROP.DATE, String.format(
				"%02X%02X.%02X.%02X - %02X%02X.%02X.%02X", d[20], d[21], d[22],
				d[23], d[24], d[25], d[26], d[27]));
	}
	
	protected ArrayList<byte[]> readLog24(Iso7816.StdTag tag, int sfi)
			throws IOException {
		final ArrayList<byte[]> ret = new ArrayList<byte[]>(MAX_LOG);
		final Iso7816.Response rsp = tag.readRecord(sfi);
		if (rsp.isOkey()) {
			addLog24(rsp, ret);
		} else {
			for (int i = 1; i <= MAX_LOG; ++i) {
				if (!addLog24(tag.readRecord(sfi, i), ret))
					break;
			}
		}

		return ret;
	}
	
	protected boolean addLog24(final Iso7816.Response r, ArrayList<byte[]> l) {
		if (!r.isOkey())
			return false;

		final byte[] raw = r.getBytes();
		final int N = raw.length - 23;
		if (N < 0)
			return false;

		for (int s = 0, e = 0; s <= N; s = e) {
			l.add(Arrays.copyOfRange(raw, s, (e = s + 23)));
		}

		return true;
	}
	
	protected void parseLog24(Application app, ArrayList<byte[]>... logs) {
		final ArrayList<String> ret = new ArrayList<String>(MAX_LOG);

		for (final ArrayList<byte[]> log : logs) {
			if (log == null)
				continue;

			for (final byte[] v : log) {
				final int money = Util.toInt(v, 5, 4);
				if (money > 0) {
					final char s = (v[9] == TRANS_CSU || v[9] == TRANS_CSU_CPX) ? '-'
							: '+';

					final int over = Util.toInt(v, 2, 3);
					final String slog;
					if (over > 0) {
						slog = String
								.format("%02X%02X.%02X.%02X %02X:%02X %c%.2f [o:%.2f] [%02X%02X%02X%02X%02X%02X]",
										v[16], v[17], v[18], v[19], v[20],
										v[21], s, (money / 100.0f),
										(over / 100.0f), v[10], v[11], v[12],
										v[13], v[14], v[15]);
					} else {
						slog = String
								.format("%02X%02X.%02X.%02X %02X:%02X %C%.2f [%02X%02X%02X%02X%02X%02X]",
										v[16], v[17], v[18], v[19], v[20],
										v[21], s, (money / 100.0f), v[10],
										v[11], v[12], v[13], v[14], v[15]);

					}

					ret.add(slog);
				}
			}
		}

		if (!ret.isEmpty())
			app.setProperty(SPEC.PROP.TRANSLOG,
					ret.toArray(new String[ret.size()]));
	}
	
	protected Application createApplication() {
		return new Application();
	}
	
	protected void configApplication(Application app) {
		app.setProperty(SPEC.PROP.ID, getApplicationId());
		app.setProperty(SPEC.PROP.CURRENCY, getCurrency());
	}
	
	protected enum HINT {
		STOP, GONEXT, RESETANDGONEXT,
	}
	
	// ISO7816-4:5.3.1.1, Selection by file identifier. 3F00 is reserved for referencing the MF
		protected final static byte[] DFI_MF = { (byte) 0x3F, (byte) 0x00 };
		protected final static byte[] DFI_EP = { (byte) 0x10, (byte) 0x01 };

	// select by DF name: 1PAY.SYS.DDF01这个是接触式的, 2PAY.SYS.DDF01是非接触的，在Quickpass里就用的非接
	protected final static byte[] DFN_PSE = { (byte) '1', (byte) 'P',
			(byte) 'A', (byte) 'Y', (byte) '.', (byte) 'S', (byte) 'Y',
			(byte) 'S', (byte) '.', (byte) 'D', (byte) 'D', (byte) 'F',
			(byte) '0', (byte) '1', };
	
	protected final static int SFI_EXTRA = 21;
	protected static int SFI_LOG = 24;
	protected static int MAX_LOG = 10;
	protected final static byte TRANS_CSU = 6;
	protected final static byte TRANS_CSU_CPX = 9;
}
