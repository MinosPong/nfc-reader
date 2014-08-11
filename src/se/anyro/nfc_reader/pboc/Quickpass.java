package se.anyro.nfc_reader.pboc;

import java.io.IOException;
import java.util.ArrayList;

import se.anyro.nfc_reader.SPEC;
import se.anyro.nfc_reader.bean.Card;
import se.anyro.nfc_reader.tech.Iso7816;
import se.anyro.nfc_reader.tech.Iso7816.BerHouse;
import se.anyro.nfc_reader.tech.Iso7816.BerTLV;

public class Quickpass extends StandardPboc {
	protected final static byte[] DFN_PPSE = { (byte) '2', (byte) 'P',
		(byte) 'A', (byte) 'Y', (byte) '.', (byte) 'S', (byte) 'Y',
		(byte) 'S', (byte) '.', (byte) 'D', (byte) 'D', (byte) 'F',
		(byte) '0', (byte) '1', };
	
	@Override
	protected SPEC.APP getApplicationId() {
		return SPEC.APP.QUICKPASS;
	}
	
	private final BerHouse topTLVs = new BerHouse();
	
	protected boolean resettag(Iso7816.StdTag tag) throws IOException {
		Iso7816.Response rsp = tag.selectByName(DFN_PPSE);
		if (!rsp.isOkey())
			return false;
		
		BerTLV.extractPrimitives(topTLVs, rsp);
		return true;
	}
	
	protected HINT readCard(Iso7816.StdTag tag, Card card) throws IOException {
		final ArrayList<Iso7816.ID> aids = getApplicationIds(tag);
	}
	
	private ArrayList<Iso7816.ID> getApplicationsIds(Iso7816.StdTag tag) throws IOException {
		final ArrayList<Iso7816.ID> ret = new ArrayList<Iso7816.ID>();
		
		// try to read DDF
		// pboc借记贷记卡-安全部分 7.4.1
		BerTLV sfi = topTLVs.findFirst(Iso7816.BerT.CLASS_SFI);
		if(sfi != null && sfi.length() == 1) {
			final int SFI = sfi.v.toInt();
			Iso7816.Response r = tag.readRecord(SFI, 1);
			for(int p = 2; r.isOkey(); ++p) {
				BerTLV.extractPrimitives(topTLVs, r);
				r = tag.readRecord(SFI, p);
			}
		}
		
		// add extracted
		
	}
}
