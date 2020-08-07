/*
 * Copyright (c) 2015, Nordic Semiconductor
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package no.nordicsemi.android.nrftoolbox.profile.multiconnect;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.sralab.emgimu.common.R;

import java.util.UUID;

import no.nordicsemi.android.nrftoolbox.AppHelpFragment;

/**
 * <p>
 * The {@link BleMulticonnectProfileServiceReadyActivity} activity is designed to be the base class for profile activities that uses services in order to connect
 * more than one device at the same time. A service extending {@link BleMulticonnectProfileService} is created when the activity is created, and the activity binds to it.
 * The service returns a binder that may be used to connect, disconnect or manage devices, and notifies the
 * activity using Local Broadcasts ({@link LocalBroadcastManager}). See {@link BleMulticonnectProfileService} for messages. If the device is not in range it will listen for
 * it and connect when it become visible. The service exists until all managed devices have been disconnected and unmanaged and the last activity unbinds from it.
 * </p>
 * <p>
 * When user closes the activity (e.g. by pressing Back button) while being connected, the Service remains working. It's remains connected to the devices or still
 * listens for updates from them. When entering back to the activity, activity will to bind to the service and refresh UI.
 * </p>
 */
public abstract class BleMulticonnectProfileServiceReadyActivity extends AppCompatActivity {
	private static final String TAG = "BleMulticonnectProfileServiceReadyActivity";

	@Override
	protected final void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// In onInitialize method a final class may register local broadcast receivers that will listen for events from the service
		onInitialize(savedInstanceState);
		// The onCreateView class should... create the view
		onCreateView(savedInstanceState);

        final Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar_actionbar);
        setSupportActionBar(toolbar);

		// Common nRF Toolbox view references are obtained here
		setUpView();
		// View is ready to be used
		onViewCreated(savedInstanceState);
	}

	/**
	 * Returns the service class for sensor communication. The service class must derive from {@link BleMulticonnectProfileService} in order to operate with this class.
	 *
	 * @return the service class
	 */
	protected abstract Class<? extends BleMulticonnectProfileService> getServiceClass();

	/**
	 * You may do some initialization here. This method is called from {@link #onCreate(Bundle)} before the view was created.
	 */
	protected void onInitialize(final Bundle savedInstanceState) {
		// empty default implementation
	}

	/**
	 * Called from {@link #onCreate(Bundle)}. This method should build the activity UI, f.e. using {@link #setContentView(int)}. Use to obtain references to
	 * views. Connect/Disconnect button, the device name view and battery level view are manager automatically.
	 *
	 * @param savedInstanceState contains the data it most recently supplied in {@link #onSaveInstanceState(Bundle)}. Note: <b>Otherwise it is null</b>.
	 */
	protected abstract void onCreateView(final Bundle savedInstanceState);

	/**
	 * Called after the view has been created.
	 *
	 * @param savedInstanceState contains the data it most recently supplied in {@link #onSaveInstanceState(Bundle)}. Note: <b>Otherwise it is null</b>.
	 */
	protected void onViewCreated(final Bundle savedInstanceState) {
		// empty default implementation
	}

	/**
	 * Called after the view and the toolbar has been created.
	 */
	protected void setUpView() {
		// set GUI
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		getMenuInflater().inflate(R.menu.help, menu);
		return true;
	}

	/**
	 * Use this method to handle menu actions other than home and about.
	 *
	 * @param itemId the menu item id
	 * @return <code>true</code> if action has been handled
	 */
	protected boolean onOptionsItemSelected(final int itemId) {
		// Overwrite when using menu other than R.menu.help
		return false;
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		final int id = item.getItemId();
        if (id == android.R.id.home)
            onBackPressed();
		else if (id == R.id.action_about) {
            final AppHelpFragment fragment = AppHelpFragment.getInstance(getAboutTextId());
            fragment.show(getSupportFragmentManager(), "help_fragment");
        } else
            return onOptionsItemSelected(id);
		return true;
	}

	/**
	 * Returns the string resource id that will be shown in About box
	 *
	 * @return the about resource id
	 */
	protected abstract int getAboutTextId();

	/**
	 * The UUID filter is used to filter out available devices that does not have such UUID in their advertisement packet. See also:
	 * {@link #isChangingConfigurations()}.
	 *
	 * @return the required UUID or <code>null</code>
	 */
	protected abstract UUID getFilterUUID();

}
