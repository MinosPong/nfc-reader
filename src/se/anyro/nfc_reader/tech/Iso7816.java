package se.anyro.nfc_reader.tech;

import java.io.IOException;

import android.nfc.tech.IsoDep;

public class Iso7816 {
	public static final byte[] EMPTY = {0};
	
	protected byte[] data;
	
	protected Iso7816() {
		data = Iso7816.EMPTY;
	}
	
	protected Iso7816(byte[] bytes) {
		data = (bytes == null) ? Iso7816.EMPTY : bytes; 
	}
	public final static class ID extends Iso7816 {
		public ID(byte... bytes) {
			super(bytes);
		}
	}
	
	public final static class StdTag {
		private final IsoDep nfcTag;
		private ID id;
		
		public StdTag(IsoDep tech) {
			nfcTag = tech;
			id = new ID(tech.getTag().getId());
		}
		
		public void connect() throws IOException {
			nfcTag.connect();
		}
	}
}
