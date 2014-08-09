package se.anyro.nfc_reader.tech;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import com.sinpo.xnfc.nfc.tech.Iso7816.Response;

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
		
		public Response selectByID(byte... id) throws IOException {
			ByteBuffer buff = ByteBuffer.allocate(id.length + 6);
			buff.put((byte) 0x00) // CLA Class
					.put((byte) 0xA4) // INS Instruction
					.put((byte) 0x00) // P1 Parameter 1
					.put((byte) 0x00) // P2 Parameter 2
					.put((byte) id.length) // Lc
					.put(id).put((byte) 0x00); // Le

			return new Response(transceive(buff.array()));
		}

		public Response selectByName(byte... name) throws IOException {
			ByteBuffer buff = ByteBuffer.allocate(name.length + 6);
			buff.put((byte) 0x00) // CLA Class
					.put((byte) 0xA4) // INS Instruction
					.put((byte) 0x04) // P1 Parameter 1
					.put((byte) 0x00) // P2 Parameter 2
					.put((byte) name.length) // Lc
					.put(name).put((byte) 0x00); // Le

			return new Response(transceive(buff.array()));
		}
		
		// see ISO7816-4 table 5
		public byte[] transceive(final byte[] cmd) throws IOException {
			try {
				byte[] rsp = null;

				byte c[] = cmd;
				do {
					byte[] r = nfcTag.transceive(c);
					if (r == null)
						break;

					int N = r.length - 2;
					if (N < 0) {
						rsp = r;
						break;
					}

					if (r[N] == CH_STA_LE) {
						c[c.length - 1] = r[N + 1];
						continue;
					}

					if (rsp == null) {
						rsp = r;
					} else {
						int n = rsp.length;
						N += n;

						rsp = Arrays.copyOf(rsp, N);

						n -= 2;
						for (byte i : r)
							rsp[n++] = i;
					}

					if (r[N] != CH_STA_MORE)
						break;

					byte s = r[N + 1];
					if (s != 0) {
						c = CMD_GETRESPONSE.clone();
					} else {
						rsp[rsp.length - 1] = CH_STA_OK;
						break;
					}

				} while (true);

				return rsp;

			} catch (Exception e) {
				return Response.ERROR;
			}
		}

		private static final byte CH_STA_OK = (byte) 0x90;
		private static final byte CH_STA_MORE = (byte) 0x61;
		private static final byte CH_STA_LE = (byte) 0x6C;
		private static final byte CMD_GETRESPONSE[] = { 0, (byte) 0xC0, 0, 0,
				0, };
	}
}
