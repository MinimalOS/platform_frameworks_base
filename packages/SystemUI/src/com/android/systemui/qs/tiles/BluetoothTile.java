/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.qs.tiles;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;

import android.widget.AdapterView;
import android.widget.ListView;
import com.android.systemui.R;
import com.android.systemui.qs.QSDetailItems;
import com.android.systemui.qs.QSDetailItems.Item;
import com.android.systemui.qs.QSDetailItemsList;
import com.android.systemui.qs.QSTile;
import com.android.systemui.statusbar.policy.BluetoothController;
import com.android.systemui.statusbar.policy.BluetoothController.PairedDevice;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Quick settings tile: Bluetooth **/
public class BluetoothTile extends QSTile<QSTile.BooleanState>  {
    private static final Intent BLUETOOTH_SETTINGS = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);

    private final BluetoothController mController;
    private final BluetoothDetailAdapter mDetailAdapter;

    public BluetoothTile(Host host) {
        super(host);
        mController = host.getBluetoothController();
        mDetailAdapter = new BluetoothDetailAdapter();
    }

    @Override
    public DetailAdapter getDetailAdapter() {
        return mDetailAdapter;
    }

    @Override
    protected BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public void setListening(boolean listening) {
        if (listening) {
            mController.addStateChangedCallback(mCallback);
        } else {
            mController.removeStateChangedCallback(mCallback);
        }
    }

    @Override
    protected void handleClick() {
        if (!isRadioProhibited()) {
            final boolean isEnabled = (Boolean)mState.value;
            mController.setBluetoothEnabled(!isEnabled);
            qsCollapsePanel();
        }
    }

    @Override
    protected void handleSecondaryClick() {
        if (!mState.value) {
            mState.value = true;
            mController.setBluetoothEnabled(true);
        }
        showDetail(true);
    }

    @Override
    protected void handleLongClick() {
        mHost.startSettingsActivity(BLUETOOTH_SETTINGS);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        final boolean supported = mController.isBluetoothSupported();
        final boolean enabled = mController.isBluetoothEnabled();
        final boolean connected = mController.isBluetoothConnected();
        final boolean connecting = mController.isBluetoothConnecting();
        state.visible = supported;
        state.value = enabled;
        state.autoMirrorDrawable = false;
        if (enabled) {
            state.label = null;
            if (connected) {
                state.icon = ResourceIcon.get(R.drawable.ic_qs_bluetooth_connected);
                state.contentDescription = mContext.getString(
                        R.string.accessibility_quick_settings_bluetooth_connected);
                state.label = mController.getLastDeviceName();
            } else if (connecting) {
                state.icon = ResourceIcon.get(R.drawable.ic_qs_bluetooth_connecting);
                state.contentDescription = mContext.getString(
                        R.string.accessibility_quick_settings_bluetooth_connecting);
                state.label = mContext.getString(R.string.quick_settings_bluetooth_label);
            } else {
                state.icon = ResourceIcon.get(R.drawable.ic_qs_bluetooth_on);
                state.contentDescription = mContext.getString(
                        R.string.accessibility_quick_settings_bluetooth_on);
            }
            if (TextUtils.isEmpty(state.label)) {
                state.label = mContext.getString(R.string.quick_settings_bluetooth_label);
            }
        } else {
            state.icon = ResourceIcon.get(R.drawable.ic_qs_bluetooth_off);
            state.label = mContext.getString(R.string.quick_settings_bluetooth_label);
            state.contentDescription = mContext.getString(
                    R.string.accessibility_quick_settings_bluetooth_off);
        }

        String bluetoothName = state.label;
        if (connected) {
            bluetoothName = state.dualLabelContentDescription = mContext.getString(
                    R.string.accessibility_bluetooth_name, state.label);
        }
        state.dualLabelContentDescription = bluetoothName;
    }

    @Override
    protected String composeChangeAnnouncement() {
        if (mState.value) {
            return mContext.getString(R.string.accessibility_quick_settings_bluetooth_changed_on);
        } else {
            return mContext.getString(R.string.accessibility_quick_settings_bluetooth_changed_off);
        }
    }

    public boolean isRadioProhibited() {
        boolean airModeOn = (android.provider.Settings.System.getInt(
                mContext.getContentResolver(),
                android.provider.Settings.System.AIRPLANE_MODE_ON, 0) != 0);
        boolean disable = mContext.getResources().getBoolean(R.bool.config_disableWifiAndBluetooth);
        return disable && airModeOn;
    }

    private final BluetoothController.Callback mCallback = new BluetoothController.Callback() {
        @Override
        public void onBluetoothStateChange(boolean enabled, boolean connecting) {
            refreshState();
        }
        @Override
        public void onBluetoothPairedDevicesChanged() {
            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    mDetailAdapter.updateItems();
                }
            });
            refreshState();
        }
    };

    private final class BluetoothDetailAdapter implements DetailAdapter,
            QSDetailItems.Callback, AdapterView.OnItemClickListener {
        private QSDetailItemsList mItemsList;
        private QSDetailItemsList.QSDetailListAdapter mAdapter;
        private List<Item> mBluetoothItems = new ArrayList<>();

        @Override
        public int getTitle() {
            return R.string.quick_settings_bluetooth_label;
        }

        @Override
        public Boolean getToggleState() {
            return mState.value;
        }

        @Override
        public Intent getSettingsIntent() {
            return BLUETOOTH_SETTINGS;
        }

        @Override
        public void setToggleState(boolean state) {
            mController.setBluetoothEnabled(state);
            fireToggleStateChanged(state);
            setItemsVisible(state);
        }

        @Override
        public View createDetailView(Context context, View convertView, ViewGroup parent) {
            mItemsList = QSDetailItemsList.convertOrInflate(context, convertView, parent);
            ListView listView = mItemsList.getListView();
            listView.setDivider(null);
            listView.setOnItemClickListener(this);
            listView.setAdapter(mAdapter =
                    new QSDetailItemsList.QSDetailListAdapter(context, mBluetoothItems));
            mAdapter.setCallback(this);
            mItemsList.setEmptyState(R.drawable.ic_qs_bluetooth_detail_empty,
                    R.string.quick_settings_bluetooth_detail_empty_text);

            updateItems();
            return mItemsList;
        }

        public void setItemsVisible(boolean visible) {
            if (mAdapter == null) return;
            if (visible) {
                updateItems();
            } else {
                mBluetoothItems.clear();
            }
            mAdapter.notifyDataSetChanged();
        }

        private void updateItems() {
            if (mAdapter == null) return;
            final Set<PairedDevice> devices = mController.getPairedDevices();
            if (devices != null) {
                mBluetoothItems.clear();
                int i = 0;
                for (PairedDevice device : devices) {
                    final Item item = new Item();
                    item.icon = R.drawable.ic_qs_bluetooth_on;
                    item.line1 = device.name;
                    if (device.state == PairedDevice.STATE_CONNECTED) {
                        item.icon = R.drawable.ic_qs_bluetooth_connected;
                        item.line2 = mContext.getString(R.string.quick_settings_connected);
                        item.canDisconnect = true;
                    } else if (device.state == PairedDevice.STATE_CONNECTING) {
                        item.icon = R.drawable.ic_qs_bluetooth_connecting;
                        item.line2 = mContext.getString(R.string.quick_settings_connecting);
                    }
                    item.tag = device;
                    mBluetoothItems.add(item);
                }
            }
            mAdapter.notifyDataSetChanged();
        }

        @Override
        public void onDetailItemClick(Item item) {
            // noop
        }

        @Override
        public void onDetailItemDisconnect(Item item) {
            if (item == null || item.tag == null) return;
            final PairedDevice device = (PairedDevice) item.tag;
            if (device != null) {
                mController.disconnect(device);
            }
        }

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            Item item = (Item) parent.getItemAtPosition(position);
            if (item == null || item.tag == null) return;
            final PairedDevice device = (PairedDevice) item.tag;
            if (device != null && device.state == PairedDevice.STATE_DISCONNECTED) {
                mController.connect(device);
            }
        }
    }
}
