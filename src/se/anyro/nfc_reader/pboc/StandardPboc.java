package se.anyro.nfc_reader.pboc;

import java.io.IOException;
import java.util.ArrayList;


import android.nfc.tech.IsoDep;
import android.util.Log;
import se.anyro.nfc_reader.bean.Card;
import se.anyro.nfc_reader.tech.Iso7816;

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
}
