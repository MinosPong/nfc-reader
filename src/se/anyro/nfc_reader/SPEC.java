package se.anyro.nfc_reader;

public final class SPEC {
	public enum PROP {
		ID(R.string.spec_prop_id),
		SERIAL(R.string.spec_prop_serial),
		PARAM(R.string.spec_prop_param),
		VERSION(R.string.spec_prop_version),
		DATE(R.string.spec_prop_date),
		CURRENCY(R.string.spec_prop_currency),
		BALANCE(R.string.spec_prop_balance),
		TRANSLOG(R.string.spec_prop_translog),
		EXCEPTION(R.string.spec_prop_exception);
		
		private final int resId;
		
		private PROP(int resId) {
			this.resId = resId;
		}
	}
	
	public enum APP {
		UNKNOWN(R.string.spec_app_unknown),
		SHENZHENTONG(R.string.spec_app_shenzhentong),
		QUICKPASS(R.string.spec_app_quickpass),
		OCTOPUS(R.string.spec_app_octopus_hk),
		BEIJINGMUNICIPAL(R.string.spec_app_beijing),
		WUHANTONG(R.string.spec_app_wuhantong),
		CHANGANTONG(R.string.spec_app_changantong),
		// Added by horseluke<horseluke@126.com> 2014.3.31
		DONGGUANTONG(R.string.spec_app_dongguantong),
		SHANGHAIGJ(R.string.spec_app_shanghai),
		DEBIT(R.string.spec_app_debit),
		CREDIT(R.string.spec_app_credit),
		QCREDIT(R.string.spec_app_qcredit);

		private final int resId;

		private APP(int resId) {
			this.resId = resId;
		}
	}
	
	public enum CUR {
		UNKNOWN(R.string.spec_cur_unknown),
		USD(R.string.spec_cur_usd),
		CNY(R.string.spec_cur_cny),
		HKD(R.string.spec_cur_hkd);

		private final int resId;

		private CUR(int resId) {
			this.resId = resId;
		}
	}
}
