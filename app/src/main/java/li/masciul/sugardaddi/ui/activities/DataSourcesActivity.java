package li.masciul.sugardaddi.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;

import java.util.ArrayList;
import java.util.List;

import li.masciul.sugardaddi.R;
import li.masciul.sugardaddi.data.sources.base.DataSource;
import li.masciul.sugardaddi.managers.DataSourceManager;
import li.masciul.sugardaddi.ui.components.DataSourceCardManager;

/**
 * DataSourcesActivity - manages registered data sources.
 *
 * Displays one {@link DataSourceCardManager} card per registered
 * {@link DataSource}, in alphabetical order. Each card exposes
 * the source's enable/disable toggle, credential fields (where applicable),
 * local database management (where applicable), and attribution text.
 *
 * Navigation: accessible from the navigation drawer, below Settings.
 */
public class DataSourcesActivity extends BaseActivity
        implements NavigationView.OnNavigationItemSelectedListener {
    private static final String TAG = "DataSourcesActivity";

    // =========================================================================
    // NAVIGATION DRAWER
    // =========================================================================

    private DrawerLayout   drawerLayout;
    private NavigationView navigationView;
    private ActionBarDrawerToggle drawerToggle;

    // =========================================================================
    // DATA SOURCE CARDS
    // =========================================================================

    /**
     * One manager per registered DataSource, in alphabetical order.
     * Created once in onBaseActivityCreated(); lifecycle hooks called
     * in onActivityResumed() / onPause() / onDestroy().
     */
    private final List<DataSourceCardManager> cardManagers = new ArrayList<>();

    // =========================================================================
    // LIFECYCLE
    // =========================================================================

    @Override
    protected void onBaseActivityCreated(Bundle savedInstanceState) {
        setContentView(R.layout.activity_data_sources);
        setupToolbar();
        setupNavigationDrawer();
        setupDataSourceCards();

        logDebug("DataSourcesActivity initialised - " + cardManagers.size() + " source(s)");
    }

    @Override
    protected void onActivityResumed() {
        super.onActivityResumed();
        if (navigationView != null) {
            MenuItem item = navigationView.getMenu().findItem(R.id.nav_data_sources);
            if (item != null) item.setChecked(true);
        }

        for (DataSourceCardManager manager : cardManagers) {
            manager.onResume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        for (DataSourceCardManager manager : cardManagers) {
            manager.onPause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        for (DataSourceCardManager manager : cardManagers) {
            manager.onDestroy();
        }
        cardManagers.clear();
    }

    // =========================================================================
    // TOOLBAR
    // =========================================================================

    private void setupToolbar() {
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.data_sources_title);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
        }
    }

    // =========================================================================
    // NAVIGATION DRAWER
    // =========================================================================

    private void setupNavigationDrawer() {
        drawerLayout   = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);

        drawerToggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(drawerToggle);
        drawerToggle.syncState();
        drawerToggle.getDrawerArrowDrawable()
                .setColor(ContextCompat.getColor(this, R.color.white));

        navigationView.setNavigationItemSelectedListener(this);
        navigationView.setCheckedItem(R.id.nav_data_sources);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if      (id == R.id.nav_search)       startActivity(new Intent(this, MainActivity.class));
        else if (id == R.id.nav_journal)      startActivity(new Intent(this, JournalActivity.class));
        else if (id == R.id.nav_create_meal)  startActivity(new Intent(this, CreateMealActivity.class));
        else if (id == R.id.nav_favorites)    startActivity(new Intent(this, FavoritesActivity.class));
        else if (id == R.id.nav_settings)     startActivity(new Intent(this, SettingsActivity.class));

        // nav_data_sources: already here - just close drawer
        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    // =========================================================================
    // DATA SOURCE CARDS
    // =========================================================================

    /**
     * Inflates one {@link DataSourceCardManager} card per registered source
     * and attaches it to the dataSourcesContainer LinearLayout.
     *
     * Sources are returned alphabetically by DataSourceManager.getAllSources().
     * No source-specific code here - each card is driven by its ManagementProvider.
     */
    private void setupDataSourceCards() {
        android.widget.LinearLayout container = findViewById(R.id.dataSourcesContainer);

        if (container == null) {
            logError("dataSourcesContainer not found in layout", null);
            return;
        }

        List<DataSource> sources = DataSourceManager.getInstance(this).getAllSources();
        for (DataSource source : sources) {
            DataSourceCardManager manager =
                    new DataSourceCardManager(source, getApplicationContext());
            manager.attach(container);
            cardManagers.add(manager);

            logDebug("Card created for: " + source.getSourceId());
        }

        logDebug("setupDataSourceCards complete - " + cardManagers.size() + " card(s)");
    }
}
