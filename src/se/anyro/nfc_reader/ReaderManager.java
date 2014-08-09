package se.anyro.nfc_reader;

import se.anyro.nfc_reader.bean.Card;
import se.anyro.nfc_reader.util.Util;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;

public final class ReaderManager {
	public static Card readCard(Tag tag) {
		final Card card = new Card();
		
		try {
			card.setProperty(SPEC.PROP.ID, Util.toHexString(tag.getId()));
			final IsoDep isodep = IsoDep.get(tag);
			if(isodep != null) {
				StandardPboc.readCard(isodep, card);
			}
			
		} catch (Exception e) {
			card.setProperty(SPEC.PROP.EXCEPTION, e);
		}
		
		return card;
	}
}
