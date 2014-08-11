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
	
	protected final static byte[] AID_DEBIT = { (byte) 0xA0, 0x00, 0x00, 0x03,
		0x33, 0x01, 0x01, 0x01 };
	protected final static byte[] AID_CREDIT = { (byte) 0xA0, 0x00, 0x00, 0x03,
		0x33, 0x01, 0x01, 0x02 };
	protected final static byte[] AID_QUASI_CREDIT = { (byte) 0xA0, 0x00, 0x00,
		0x03, 0x33, 0x01, 0x01, 0x03 };
	
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
		}
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
	}
}
