/*
 * Beaconator scans for Bluetooth LE devices and recods the scans to a database.
 * Based on the Open Source BluetoothLeGatt Android program 
 * (see http://developer.android.com/samples/BluetoothLeGatt/index.html)
 * Copyright (C) 2014  Jesse M. Blum

 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.

 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package uk.ac.horizon.beaconator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import android.app.Activity;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Activity for scanning and displaying available Bluetooth LE devices.
 */
public class BtLeScan extends ListActivity {
	private LeDeviceListAdapter mLeDeviceListAdapter;
	private BluetoothAdapter mBluetoothAdapter;
	private boolean mScanning;
	private Handler mHandler;
	private ScanData scandata2 = new ScanData();
	final static char[] hexArray = "0123456789ABCDEF".toCharArray();

	private static final int REQUEST_ENABLE_BT = 1;
	// Stops scanning after 30 seconds.
	private static final long SCAN_PERIOD = 30000;
	private long session = -1;
	private final int STARTBYTE = 5;	// used for the beacon record

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getActionBar().setTitle(R.string.title_devices);
		mHandler = new Handler();
		session = new Date().getTime();

		// Use this check to determine whether BLE is supported on the device.
		// Then you can
		// selectively disable BLE-related features.
		if (!getPackageManager().hasSystemFeature(
				PackageManager.FEATURE_BLUETOOTH_LE)) {
			Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT)
					.show();
			finish();
		}

		// Initializes a Bluetooth adapter. For API level 18 and above, get a
		// reference to
		// BluetoothAdapter through BluetoothManager.
		final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
		mBluetoothAdapter = bluetoothManager.getAdapter();

		// Checks if Bluetooth is supported on the device.
		if (mBluetoothAdapter == null) {
			Toast.makeText(this, R.string.error_bluetooth_not_supported,
					Toast.LENGTH_SHORT).show();
			finish();
			return;
		}
	}
	
	/**
	 * Inserts an observation into the database
	 * @param device is a BluetoothDevice
	 * @param position is the position of the item within the adapter's 
	 * data set of the item whose view we want
	 * @return
	 */
	public String addObservation(BluetoothDevice device, int position) {
		long timestamp = new Date().getTime();
		// Create a new map of values, where column names are the keys
		ContentValues values = new ContentValues();
		byte[] scanData = mLeDeviceListAdapter.getLeDevices().
				get(position).scanRecord;
		
		values.put(Database.ObservationColumns.COLUMN_NAME_OBS_TIME,
				timestamp);
		values.put(Database.ObservationColumns.COLUMN_NAME_SESSION,
				session);
		String uuid = mLeDeviceListAdapter.getUUID(position, scanData);
		values.put(Database.ObservationColumns.COLUMN_NAME_UUID,
				uuid);
		String devname = device.getName();
		values.put(Database.ObservationColumns.COLUMN_NAME_DEV_NAME,
				devname);
		int rssi = mLeDeviceListAdapter.getRssi(position);
		values.put(Database.ObservationColumns.COLUMN_NAME_RSSI,
				rssi);
		int major = mLeDeviceListAdapter.getMajor(scanData);
		values.put(Database.ObservationColumns.COLUMN_NAME_MAJOR,
				major);
		int minor = mLeDeviceListAdapter.getMinor(scanData);
		values.put(Database.ObservationColumns.COLUMN_NAME_MINOR,
				minor);
		int txPower = mLeDeviceListAdapter.getTxPower(scanData);
		values.put(Database.ObservationColumns.COLUMN_NAME_TXPOWER,
				txPower);
		
		Database.getInstance(this).getDb().insert(
				Database.ObservationColumns.TABLE_NAME, null, values);
		
		return devname + "\n"+  uuid + "\nrssi: " + rssi + "\ntxPower: " + txPower + 
				"\nmajor: " + major + " minor: " + minor ;
	}
	
	/**
	 * Outputs the database to SD card /BackupFolder/DATABASE_NAME
	 * @param context
	 */
	public void dumpDB(Context context){
		Database.exportDB(context);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		if (!mScanning) {
			menu.findItem(R.id.menu_stop).setVisible(false);
			menu.findItem(R.id.menu_scan).setVisible(true);
		} else {
			menu.findItem(R.id.menu_stop).setVisible(true);
			menu.findItem(R.id.menu_scan).setVisible(false);
		}
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menu_scan:
				mLeDeviceListAdapter.clear();
				scanLeDevice(true);
				break;
			case R.id.menu_stop:
				scanLeDevice(false);
				break;
			case R.id.action_backup:
				actionBackup();
				break;
		}
		return true;
	}

	@Override
	protected void onResume() {
		super.onResume();

		// Ensures Bluetooth is enabled on the device. If Bluetooth is not
		// currently enabled,
		// fire an intent to display a dialog asking the user to grant
		// permission to enable it.
		if (!mBluetoothAdapter.isEnabled()) {
			if (!mBluetoothAdapter.isEnabled()) {
				Intent enableBtIntent = new Intent(
						BluetoothAdapter.ACTION_REQUEST_ENABLE);
				startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
			}
		}

		// Initializes list view adapter.
		mLeDeviceListAdapter = new LeDeviceListAdapter();
		setListAdapter(mLeDeviceListAdapter);
		scanLeDevice(true);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		// User chose not to enable Bluetooth.
		if (requestCode == REQUEST_ENABLE_BT
				&& resultCode == Activity.RESULT_CANCELED) {
			finish();
			return;
		}
		super.onActivityResult(requestCode, resultCode, data);
	}
	
	/**
	 * On backup: Backup database to text file. If the preference for resetting
	 * the db is toggled on then empty the DB tables.
	 */
	private void actionBackup() {
		Toast.makeText(this, "Backing up database", Toast.LENGTH_SHORT)
				.show();
		dumpDB(this);
	}

	@Override
	protected void onPause() {
		super.onPause();
		scanLeDevice(false);
		mLeDeviceListAdapter.clear();
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		final BluetoothDevice device = mLeDeviceListAdapter.getDevice(position);
		if (device == null)
			return;
		// final Intent intent = new Intent(this, DeviceControlActivity.class);
		// intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_NAME,
		// device.getName());
		// intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_ADDRESS,
		// device.getAddress());
		if (mScanning) {
			mBluetoothAdapter.stopLeScan(mLeScanCallback);
			mScanning = false;
		}
		// startActivity(intent);
	}

	private void scanLeDevice(final boolean enable) {
		if (enable) {
			// Stops scanning after a pre-defined scan period.
			session = new Date().getTime();
			mHandler.postDelayed(new Runnable() {
				@Override
				public void run() {
					mScanning = false;
					mBluetoothAdapter.stopLeScan(mLeScanCallback);
					invalidateOptionsMenu();
				}
			}, SCAN_PERIOD);

			mScanning = true;
			mBluetoothAdapter.startLeScan(mLeScanCallback);
		} else {
			mScanning = false;
			mBluetoothAdapter.stopLeScan(mLeScanCallback);
		}
		invalidateOptionsMenu();
	}

	private class BTDevice {
		public BluetoothDevice device;
		public int rssi;
		//List<ScanData> scanRecord;
		byte[] scanRecord;

		public BTDevice(BluetoothDevice devIn, int rssiIn, byte[] scanRecordIn) {
			device = devIn;
			rssi = rssiIn;
			//scanRecord = scandata.parseScanRecord(scanRecordIn);
			scanRecord = scanRecordIn;
		}
	}
	
	/**
	 * Parses the scan record
	 *
	 */
	private class ScanData {
		int length;
		int type;
		byte[] data;
		
		public ScanData(){
			length = 0;
			type = 0;
			data = null;
		}

		public ScanData(int lengthIn, int typeIn, byte[] dataIn) {
			length = lengthIn;
			type = typeIn;
			data = dataIn;
		}

		/*
		 * Based on http://www.doubleencore.com/2013/12/bluetooth-smart-for-android/
		 */
		public List<ScanData> parseScanRecord(byte[] scanRecord) {
			List<ScanData> scanData = new ArrayList<ScanData>();

			int index = 0;
			while (index < scanRecord.length) {
				int length = scanRecord[index++];
				// Done once we run out of records
				if (length == 0)
					break;

				int type = scanRecord[index];
				// Done if our record isn't a valid type
				if (type == 0)
					break;

				byte[] data = Arrays.copyOfRange(scanRecord, index + 1,
						index + length);

				scanData.add(new ScanData(length, type, data));
				// Advance
				index += length;
			}
			return scanData;
		}
		
		// Base on http://stackoverflow.com/questions/9655181/convert-from-byte-array-to-hex-string-in-java
		public String bytesToHex(byte[] bytes) {
		    char[] hexChars = new char[bytes.length * 2];
		    for ( int j = 0; j < bytes.length; j++ ) {
		        int v = bytes[j] & 0xFF;
		        hexChars[j * 2] = hexArray[v >>> 4];
		        hexChars[j * 2 + 1] = hexArray[v & 0x0F];
		    }
		    String pureChars = new String(hexChars);
		    return pureChars;
		    /*// Add a ":" every 2 chars
		    char[] chars = new char[pureChars.length() + (pureChars.length() / 2)];
		    int offset = pureChars.length() % 4;
		    int idx = 0, strIdx = 0;
		    for (; strIdx < pureChars.length(); idx++, strIdx++)
		    {
		        if (((strIdx % 2) == offset) && (strIdx != 0))
		            chars[idx++] = ':';
		        chars[idx] = pureChars.charAt(strIdx);
		    }
		    return new String(chars);*/
		}
	}

	// Adapter for holding devices found through scanning.
	private class LeDeviceListAdapter extends BaseAdapter {
		private ArrayList<BTDevice> mLeDevices;
		private LayoutInflater mInflator;

		public LeDeviceListAdapter() {
			super();
			mLeDevices = new ArrayList<BTDevice>();
			mInflator = BtLeScan.this.getLayoutInflater();
		}
		
		public ArrayList<BTDevice> getLeDevices(){
			return mLeDevices;
		}

		public void addDevice(BluetoothDevice device, int rssi,
				byte[] scanRecord) {
			if (!mLeDevices.contains(device)) {
				mLeDevices.add(new BTDevice(device, rssi, scanRecord));
			}
		}

		public BluetoothDevice getDevice(int position) {
			return mLeDevices.get(position).device;
		}

		public int getRssi(int position) {
			return mLeDevices.get(position).rssi;
		}

		public String getUUID(int position, byte[] scanData){
			byte[] proximityUuidBytes = new byte[16];
			System.arraycopy(scanData, STARTBYTE + 4, proximityUuidBytes, 0, 16);
			String hexString = scandata2.bytesToHex(proximityUuidBytes);
			StringBuilder sb = new StringBuilder();
			sb.append(hexString.substring(0, 8));
			sb.append("-");
			sb.append(hexString.substring(8, 12));
			sb.append("-");
			sb.append(hexString.substring(12, 16));
			sb.append("-");
			sb.append(hexString.substring(16, 20));
			sb.append("-");
			sb.append(hexString.substring(20, 32));
		    return sb.toString();
		}

		/**
		 * Returns the Major value of the give scanData
		 * @param scanData is a device's scanRecord
		 * @return the major value as an int
		 */
		public int getMajor(byte[] scanData){
			return (scanData[(STARTBYTE + 20)] & 0xFF) * 
					256 + (scanData[(STARTBYTE + 21)] & 0xFF);
		}

		/**
		 * Returns the Minor value of the give scanData
		 * @param scanData is a device's scanRecord
		 * @return the minor value as an int
		 */
		public int getMinor(byte[] scanData){
			return (scanData[(STARTBYTE + 22)] & 0xFF) *
					256 + (scanData[(STARTBYTE + 23)] & 0xFF);
		}

		/**
		 * Returns the transmission power value of the give scanData
		 * @param scanData is a device's scanRecord
		 * @return the transmission power value as an int
		 */
		public int getTxPower(byte[] scanData){
			return scanData[(STARTBYTE + 24)];
		}
		
		/**
		 * Returns a full scan record's human readable data
		 * @param scanData is a device's scanRecord
		 * @return A formatted String with the readable data
		 */
		public String getScanRecord(byte[] scanData) {
			/*List<ScanData> scanRecords = mLeDevices.get(position).scanRecord;
			StringBuilder sb = new StringBuilder();
			for(ScanData record : scanRecords) {
				sb.append(record.type);
				sb.append(":");
				sb.append(record.length -1 );
				sb.append(":");
				sb.append(scandata.bytesToHex(record.data));
				sb.append("\n");
			}
			return sb.toString();*/
			int startByte = 5;
			StringBuilder sb = new StringBuilder();
			sb.append("major: ");
			sb.append(((scanData[(startByte + 20)] & 0xFF) * 256 + (scanData[(startByte + 21)] & 0xFF)));
			sb.append("\nminor: ");
			sb.append(((scanData[(startByte + 22)] & 0xFF) * 256 + (scanData[(startByte + 23)] & 0xFF)));
			sb.append("\ntxPower: ");
			sb.append(scanData[(startByte + 24)]);
			
			byte[] proximityUuidBytes = new byte[16];
		     System.arraycopy(scanData, startByte + 4, proximityUuidBytes, 0, 16);
		     String hexString = scandata2.bytesToHex(proximityUuidBytes);
		     sb.append("\nuuid: ");
		     sb.append(hexString.substring(0, 8));
		     sb.append("-");
		     sb.append(hexString.substring(8, 12));
		     sb.append("-");
		     sb.append(hexString.substring(12, 16));
		     sb.append("-");
		     sb.append(hexString.substring(16, 20));
		     sb.append("-");
		     sb.append(hexString.substring(20, 32));
			return sb.toString();
			//return scandata.bytesToHex(mLeDevices.get(position).scanRecord);
		}

		public void clear() {
			mLeDevices.clear();
		}

		@Override
		public int getCount() {
			return mLeDevices.size();
		}

		@Override
		public Object getItem(int i) {
			return mLeDevices.get(i);
		}

		@Override
		public long getItemId(int i) {
			return i;
		}
	
		@Override
		public View getView(int i, View view, ViewGroup viewGroup) {
			ViewHolder viewHolder;
			// General ListView optimization code.
			if (view == null) {
				view = mInflator.inflate(R.layout.listitem_device, null);
				viewHolder = new ViewHolder();
				viewHolder.deviceAddress = (TextView) view
						.findViewById(R.id.device_address);
				viewHolder.deviceName = (TextView) view
						.findViewById(R.id.device_name);
				view.setTag(viewHolder);
			} else {
				viewHolder = (ViewHolder) view.getTag();
			}

			BluetoothDevice device = this.getDevice(i);
			final String deviceDetails = addObservation(device, i);
			/*final String deviceDetails = device.getName() + " "
					+ this.getRssi(i) + " " + this.getScanRecord(i);*/
			if (deviceDetails != null && deviceDetails.length() > 0)
				viewHolder.deviceName.setText(deviceDetails);
			else
				viewHolder.deviceName.setText(R.string.unknown_device);
			viewHolder.deviceAddress.setText(device.getAddress());

			return view;
		}
	}

	// Device scan callback.
	private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {

		@Override
		public void onLeScan(final BluetoothDevice device, final int rssi,
				final byte[] scanRecord) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					mLeDeviceListAdapter.addDevice(device, rssi, scanRecord);
					mLeDeviceListAdapter.notifyDataSetChanged();
				}
			});
		}
	};

	static class ViewHolder {
		TextView deviceName;
		TextView deviceAddress;
	}
}