/* NFCard is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 3 of the License, or
(at your option) any later version.

NFCard is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with Wget.  If not, see <http://www.gnu.org/licenses/>.

Additional permission under GNU GPL version 3 section 7 */

package com.sinpo.xnfc;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.xml.sax.XMLReader;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.nfc.FormatException;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.ClipboardManager;
import android.text.Editable;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;
import android.widget.Toast;

import com.sinpo.xnfc.card.CardManager;

@SuppressLint("NewApi")
public final class NFCard extends Activity implements OnClickListener, Html.ImageGetter, Html.TagHandler {
	private NfcAdapter nfcAdapter;
	private PendingIntent pendingIntent;
	private Resources res;
	private TextView board;

	private enum ContentType {
		HINT, DATA, MSG
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.nfcard);

		final Resources res = getResources();
		this.res = res;

		final View decor = getWindow().getDecorView();
		final TextView board = (TextView) decor.findViewById(R.id.board);
		this.board = board;

		decor.findViewById(R.id.btnCopy).setOnClickListener(this);
		decor.findViewById(R.id.btnNfc).setOnClickListener(this);
		decor.findViewById(R.id.btnExit).setOnClickListener(this);

		board.setMovementMethod(LinkMovementMethod.getInstance());
		board.setFocusable(false);
		board.setClickable(false);
		board.setLongClickable(false);

		nfcAdapter = NfcAdapter.getDefaultAdapter(this);
		pendingIntent = PendingIntent.getActivity(this, 0,
				new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);

		onNewIntent(getIntent());
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.clear:
			showData(null);
			return true;
		case R.id.help:
			showHelp(R.string.info_help);
			return true;
		case R.id.about:
			showHelp(R.string.info_about);
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	protected void onPause() {
		super.onPause();

		if (nfcAdapter != null)
			nfcAdapter.disableForegroundDispatch(this);
	}

	@Override
	protected void onResume() {
		super.onResume();

		if (nfcAdapter != null)
			nfcAdapter.enableForegroundDispatch(this, pendingIntent, CardManager.FILTERS, CardManager.TECHLISTS);

		refreshStatus();
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		try {
			if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(intent.getAction())) {
				// final Parcelable p =
				// intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
				Log.d("NFCTAG", intent.getAction());
				Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
//				Parcelable[] rawMsgs = getIntent().getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
				String read = read(tag);
				Log.e("rawMsgs", read + "");
				// try {
				// final Tag tag = (Tag) p;
				// final IsoDep isodep = IsoDep.get(tag);
				// final Iso7816.Tag tag1 = new Iso7816.Tag(isodep);
				// // read(tag1);
				// } catch (Exception e) {
				// }
				// showData((p != null) ? CardManager.load(p, res) : null);
			}
		} catch (Exception e) {
			// TODO: handle exception
		}
	}

	// 读取方法
	private String read(Tag tagFromIntent) throws IOException, FormatException {
		String str = "";
		try {
			// Get an instance of the type A card from this TAG
			IsoDep isodep = IsoDep.get(tagFromIntent);
			isodep.connect();
			// select the card manager applet
			byte[] mf = { (byte) '1', (byte) 'P', (byte) 'A', (byte) 'Y', (byte) '.', (byte) 'S', (byte) 'Y',
					(byte) 'S', (byte) '.', (byte) 'D', (byte) 'D', (byte) 'F', (byte) '0', (byte) '1', };
			byte[] mfRsp = isodep.transceive(getSelectCommand(mf));
			Log.d("", "mfRsp:" + HexToString(mfRsp));
			// select Main Application
			byte[] szt = { (byte) 'P', (byte) 'A', (byte) 'Y', (byte) '.', (byte) 'S', (byte) 'Z', (byte) 'T' };
			byte[] sztRsp = isodep.transceive(getSelectCommand(szt));
			Log.d("", "sztRsp:" + HexToString(sztRsp));

			byte[] balance = { (byte) 0x80, (byte) 0x5C, 0x00, 0x02, 0x04 };
			byte[] balanceRsp = isodep.transceive(balance);
			Log.d("", "balanceRsp:" + HexToString(balanceRsp));
			if (balanceRsp != null && balanceRsp.length > 4) {
				int cash = byteToInt(balanceRsp, 4);
				float ba = cash / 100.0f;
				str = "Balance:" + ba;
			}
			isodep.close();
		} catch (Exception e) {
			Log.d("", "ERROR:" + e.getMessage());
		}
		return str;
	}

	private byte[] getSelectCommand(byte[] aid) {
		final ByteBuffer cmd_pse = ByteBuffer.allocate(aid.length + 6);
		cmd_pse.put((byte) 0x00) // CLA Class
				.put((byte) 0xA4) // INS Instruction
				.put((byte) 0x04) // P1 Parameter 1
				.put((byte) 0x00) // P2 Parameter 2
				.put((byte) aid.length) // Lc
				.put(aid).put((byte) 0x00); // Le
		return cmd_pse.array();
	}

	private String HexToString(byte[] data) {
		String temp = "";
		for (byte d : data) {
			temp += String.format("%02x", d);
		}
		return temp;
	}

	public static byte byteToHex(byte arg) {
		byte hex = 0;
		if (arg >= 48 && arg <= 57) {
			hex = (byte) (arg - 48);
		} else if (arg >= 65 && arg <= 70) {
			hex = (byte) (arg - 55);
		} else if (arg >= 97 && arg <= 102) {
			hex = (byte) (arg - 87);
		}
		return hex;
	}

	private byte[] StringToHex(String data) {
		byte temp[] = data.getBytes();
		byte result[] = new byte[temp.length / 2];
		for (int i = 0; i < result.length; i++) {
			result[i] = (byte) (byteToHex(temp[i * 2]) << 4 | byteToHex(temp[i * 2 + 1]));
		}
		return result;
	}

	private int byteToInt(byte[] b, int n) {
		int ret = 0;
		for (int i = 0; i < n; i++) {
			ret = ret << 8;
			ret |= b[i] & 0x00FF;
		}
		if (ret > 100000 || ret < -100000)
			ret -= 0x80000000;
		return ret;
	}

	@Override
	public void onClick(final View v) {
		switch (v.getId()) {
		case R.id.btnCopy: {
			copyData();
			break;
		}
		case R.id.btnNfc: {
			startActivityForResult(new Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS), 0);
			break;
		}
		case R.id.btnExit: {
			finish();
			break;
		}
		default:
			break;
		}
	}

	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		refreshStatus();
	}

	private void refreshStatus() {
		final Resources r = this.res;

		final String tip;
		if (nfcAdapter == null)
			tip = r.getString(R.string.tip_nfc_notfound);
		else if (nfcAdapter.isEnabled())
			tip = r.getString(R.string.tip_nfc_enabled);
		else
			tip = r.getString(R.string.tip_nfc_disabled);

		final StringBuilder s = new StringBuilder(r.getString(R.string.app_name));

		s.append("  --  ").append(tip);
		setTitle(s);

		final CharSequence text = board.getText();
		if (text == null || board.getTag() == ContentType.HINT)
			showHint();
	}

	@SuppressWarnings("deprecation")
	private void copyData() {
		final CharSequence text = board.getText();
		if (text == null || board.getTag() != ContentType.DATA)
			return;

		((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE)).setText(text);

		final String msg = res.getString(R.string.msg_copied);
		final Toast toast = Toast.makeText(this, msg, Toast.LENGTH_SHORT);
		toast.setGravity(Gravity.CENTER, 0, 0);
		toast.show();
	}

	private void showData(String data) {
		if (data == null || data.length() == 0) {
			showHint();
			return;
		}

		final TextView board = this.board;
		final Resources res = this.res;

		final int padding = res.getDimensionPixelSize(R.dimen.pnl_margin);

		board.setPadding(padding, padding, padding, padding);
		board.setTypeface(Typeface.defaultFromStyle(Typeface.NORMAL));
		board.setTextSize(res.getDimension(R.dimen.text_small));
		board.setTextColor(res.getColor(R.color.text_default));
		board.setGravity(Gravity.NO_GRAVITY);
		board.setTag(ContentType.DATA);
		board.setText(Html.fromHtml(data));
	}

	private void showHelp(int id) {
		final TextView board = this.board;
		final Resources res = this.res;

		final int padding = res.getDimensionPixelSize(R.dimen.pnl_margin);

		board.setPadding(padding, padding, padding, padding);
		board.setTypeface(Typeface.defaultFromStyle(Typeface.NORMAL));
		board.setTextSize(res.getDimension(R.dimen.text_small));
		board.setTextColor(res.getColor(R.color.text_default));
		board.setGravity(Gravity.NO_GRAVITY);
		board.setTag(ContentType.MSG);
		board.setText(Html.fromHtml(res.getString(id), this, this));
	}

	private void showHint() {
		final TextView board = this.board;
		final Resources res = this.res;
		final String hint;

		if (nfcAdapter == null)
			hint = res.getString(R.string.msg_nonfc);
		else if (nfcAdapter.isEnabled())
			hint = res.getString(R.string.msg_nocard);
		else
			hint = res.getString(R.string.msg_nfcdisabled);

		final int padding = res.getDimensionPixelSize(R.dimen.text_middle);

		board.setPadding(padding, padding, padding, padding);
		board.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD_ITALIC));
		board.setTextSize(res.getDimension(R.dimen.text_middle));
		board.setTextColor(res.getColor(R.color.text_tip));
		board.setGravity(Gravity.CENTER_VERTICAL);
		board.setTag(ContentType.HINT);
		board.setText(Html.fromHtml(hint));
	}

	@Override
	public void handleTag(boolean opening, String tag, Editable output, XMLReader xmlReader) {
		if (!opening && "version".equals(tag)) {
			try {
				output.append(getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
			} catch (NameNotFoundException e) {
			}
		}
	}

	@Override
	public Drawable getDrawable(String source) {
		final Resources r = getResources();

		final Drawable ret;
		final String[] params = source.split(",");
		if ("icon_main".equals(params[0])) {
			ret = r.getDrawable(R.drawable.ic_app_main);
		} else {
			ret = null;
		}

		if (ret != null) {
			final float f = r.getDisplayMetrics().densityDpi / 72f;
			final int w = (int) (Util.parseInt(params[1], 10, 16) * f + 0.5f);
			final int h = (int) (Util.parseInt(params[2], 10, 16) * f + 0.5f);
			ret.setBounds(0, 0, w, h);
		}

		return ret;
	}
}
