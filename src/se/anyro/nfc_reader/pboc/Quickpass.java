package se.anyro.nfc_reader.pboc;

import java.io.IOException;
import java.util.ArrayList;

import se.anyro.nfc_reader.SPEC;
import se.anyro.nfc_reader.bean.Application;
import se.anyro.nfc_reader.bean.Card;
import se.anyro.nfc_reader.tech.Iso7816;
import se.anyro.nfc_reader.tech.Iso7816.BerHouse;
import se.anyro.nfc_reader.tech.Iso7816.BerTLV;
import se.anyro.nfc_reader.util.Util;

public class Quickpass extends StandardPboc {
	protected final static byte[] DFN_PPSE = { (byte) '2', (byte) 'P',
		(byte) 'A', (byte) 'Y', (byte) '.', (byte) 'S', (byte) 'Y',
		(byte) 'S', (byte) '.', (byte) 'D', (byte) 'D', (byte) 'F',
		(byte) '0', (byte) '1', };
	
	protected final static byte[] AID_DEBIT = { (byte) 0xA0, 0x00, 0x00, 0x03,
		0x33, 0x01, 0x01, 0x01 };
	protected final static byte[] AID_CREDIT = { (byte) 0xA0, 0x00, 0x00, 0x03,
		0x33, 0x01, 0x01, 0x02 };
	protected final static byte[] AID_QUASI_CREDIT = { (byte) 0xA0, 0x00, 0x00,
		0x03, 0x33, 0x01, 0x01, 0x03 };
	
	public final static short MARK_LOG = (short) 0xDFFF;
	
	protected final static short[] TAG_GLOBAL = { (short) 0x9F79 /* 电子现金金额 */,
		(short) 0x9F78 /* 电子现金单笔上限 */, (short) 0x9F77 /* 电子现金余额上限 */,
		(short) 0x9F13 /* 联机ATC */, (short) 0x9F36 /* ATC */,
		(short) 0x9F51 /* 货币代码 */, (short) 0x9F4F /* 日志文件格式 */,
		(short) 0x9F4D /* 日志文件ID */, (short) 0x5A /* 帐号 */,
		(short) 0x5F24 /* 失效日期*/, (short) 0x5F25 /* 生效日期 */, };
	
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
		
		for(Iso7816.ID aid : aids) {
			// select application
			// AID就是ADF
			Iso7816.Response rsp = tag.selectByName(aid.getBytes());
			if (!rsp.isOkey())
				continue;
			
			final BerHouse subTLVs = new BerHouse();
			
			// collect info
			BerTLV.extractPrimitives(subTLVs, rsp);
			collectTLVFromGlobalTags(tag, subTLVs);
			
			/*--------------------------------------------------------------*/
			// parse PDOL and get processing options
			// 这是正规途径，但是每次GPO都会使ATC加1，达到65535卡片就锁定了
			/*--------------------------------------------------------------*/
			// rsp = tag.getProcessingOptions(buildPDOL(subTLVs));
			// if (rsp.isOkey())
			// BerTLV.extractPrimitives(subTLVs, rsp);
			
			/*--------------------------------------------------------------*/
			// 遍历目录下31个文件，山寨途径，微暴力，不知会对卡片折寿多少
            // 相对于GPO不停的增加ATC，这是一种折中
            // (遍历过程一般不会超过15个文件就会结束)
			/*--------------------------------------------------------------*/
			collectTLVFromRecords(tag, subTLVs);
			
			final Application app = createApplication();
			parseInfo(app, subTLVs);
			parseLogs(app, subTLVs);
			card.addApplication(app);
		}
	}
	
	private static void parseInfo(Application app, BerHouse tlvs) {
		// 账号
		Object prop = parseString(tlvs, (short)0x5A);
		if(prop != null)
			app.setProperty(SPEC.PROP.SERIAL, prop);
		
		prop = parseApplicationName(tlvs, (String)prop);
		if(prop != null)
			app.setProperty(SPEC.PROP.ID, prop);
		
		prop = parseInteger(tlvs, (short)0x9F08);
		if(prop != null)
			app.setProperty(SPEC.PROP.VERSION, prop);
		
		// ATC
		prop = parseInteger(tlvs, (short)0x9F36);
		if(prop != null)
			app.setProperty(SPEC.PROP.COUNT, prop);
		
		prop = parseValidity(tlvs, (short)0x5F25, (short)0x5F24);
		if(prop != null)
			app.setProperty(SPEC.PROP.DATE, prop);
	}
	
	private static SPEC.APP parseApplicationName(BerHouse tlvs, String serial) {
		// 0x84是什么tag?
		String f = parseString(tlvs, (short)0x84);
		if(f != null) {
			if(f.endsWith("010101"))
				return SPEC.APP.DEBIT;
			
			if(f.endsWith("010102"))
				return SPEC.APP.CREDIT;
			
			if(f.endsWith("010103"))
				return SPEC.APP.QCREDIT;
		}
		return SPEC.APP.UNKNOWN;
	}
	
	private static String parseString(BerHouse tlvs, short tag) {
		final byte[] v = BerTLV.getValue(tlvs.findFirst(tag));
		return (v != null) ? Util.toHexString(v) : null;
	}
	
	private static Integer parseInteger(BerHouse tlvs, short tag) {
		final byte[] v = BerTLV.getValue(tlvs.findFirst(tag));
		return (v != null) ? Util.toInt(v) : null;
	}
	
	private static String parseValidity(BerHouse tlvs, short from, short to) {
		final byte[] f = BerTLV.getValue(tlvs.findFirst(from));
		final byte[] t = BerTLV.getValue(tlvs.findFirst(to));
		
		if(t == null || t.length != 3 || t[0] == 0 || t[0] == (byte)0xFF)
			return null;
		
		if(f == null || f.length != 3 || f[0] == 0 || f[0] == (byte)0xFF)
			return String.format("? - 20%02x.%02x.%02x", t[0], t[1], t[2]);
		
		return String.format("20%02x.%02x.%02x - 20%02x.%02x.%02x", f[0], f[1],
				f[2], t[0], t[1], t[2]);
	}
	
	private ArrayList<Iso7816.ID> getApplicationIds(Iso7816.StdTag tag) throws IOException {
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
		// 找出卡片所支持的aid
		ArrayList<BerTLV> aids = topTLVs.findAll(Iso7816.BerT.CLASS_AID);
		if(aids != null) {
			for(BerTLV aid : aids) {
				ret.add(new Iso7816.ID(aid.v.getBytes()));
			}
		}
		
		// use default list
		if(ret.isEmpty()) {
			ret.add(new Iso7816.ID(AID_DEBIT));
			ret.add(new Iso7816.ID(AID_CREDIT));
			ret.add(new Iso7816.ID(AID_QUASI_CREDIT));
		}
		
		return ret;
	}
	
	private static void collectTLVFromGlobalTags(Iso7816.StdTag tag, BerHouse tlvs)
		throws IOException {
		for(short t : TAG_GLOBAL) {
			Iso7816.Response r = tag.getData(t);
			if(r.isOkey()) {
				tlvs.add(BerTLV.read(r));
			}
		}
	}
	
	private static void collectTLVFromRecords(Iso7816.StdTag tag, BerHouse tlvs)
		throws IOException {
		// info files
		for(int sfi = 1; sfi <= 10; sfi++) {
			Iso7816.Response r = tag.readRecord(sfi, 1);
			for (int idx = 2; r.isOkey() && idx <= 10; ++idx) {
				BerTLV.extractPrimitives(tlvs, r);
				r = tag.readRecord(sfi, idx);
			}
		}
		
		// check if already get sfi of log file
		BerTLV logEntry = tlvs.findFirst((short) 0x9F4D);
		
		final int S, E;
		if (logEntry != null && logEntry.length() == 2) {
			S = E = logEntry.v.getBytes()[0] & 0x000000FF;
		} else {
			S = 11;
			E = 31;
		}
		
		// log files
		for (int sfi = S; sfi <= E; ++sfi) {
			Iso7816.Response r = tag.readRecord(sfi, 1);
			boolean findOne = r.isOkey();

			for (int idx = 2; r.isOkey() && idx <= 10; ++idx) {
				tlvs.add(MARK_LOG, r);
				r = tag.readRecord(sfi, idx);
			}

			if (findOne)
				break;
		}
	}
}
