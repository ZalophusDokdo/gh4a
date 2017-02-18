/*
 * Copyright 2011 Azwan Adli Abdullah
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gh4a.activities;

import java.io.IOException;
import java.util.List;

import org.eclipse.egit.github.core.CodeSearchResult;
import org.eclipse.egit.github.core.Repository;
import org.eclipse.egit.github.core.RequestError;
import org.eclipse.egit.github.core.SearchUser;
import org.eclipse.egit.github.core.client.RequestException;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.CursorAdapter;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import com.gh4a.BackgroundTask;
import com.gh4a.BaseActivity;
import com.gh4a.R;
import com.gh4a.adapter.CodeSearchAdapter;
import com.gh4a.adapter.RepositoryAdapter;
import com.gh4a.adapter.RootAdapter;
import com.gh4a.adapter.SearchUserAdapter;
import com.gh4a.db.SuggestionsProvider;
import com.gh4a.loader.CodeSearchLoader;
import com.gh4a.loader.LoaderCallbacks;
import com.gh4a.loader.LoaderResult;
import com.gh4a.loader.RepositorySearchLoader;
import com.gh4a.loader.UserSearchLoader;
import com.gh4a.utils.StringUtils;
import com.gh4a.utils.UiUtils;
import com.gh4a.widget.DividerItemDecoration;

public class SearchActivity extends BaseActivity implements
        SearchView.OnQueryTextListener, SearchView.OnCloseListener, SearchView.OnSuggestionListener,
        AdapterView.OnItemSelectedListener, RootAdapter.OnItemClickListener {

    private RootAdapter<?, ?> mAdapter;
    private RecyclerView mResultsView;

    private Spinner mSearchType;
    private SearchView mSearch;
    private String mQuery;

    private static final String STATE_KEY_QUERY = "query";
    private static final String STATE_KEY_SEARCH_MODE = "search_mode";
    private static final int SEARCH_MODE_NONE = 0;
    private static final int SEARCH_MODE_REPO = 1;
    private static final int SEARCH_MODE_USER = 2;
    private static final int SEARCH_MODE_CODE = 3;

    private final LoaderCallbacks<List<Repository>> mRepoCallback = new LoaderCallbacks<List<Repository>>(this) {
        @Override
        protected Loader<LoaderResult<List<Repository>>> onCreateLoader() {
            RepositorySearchLoader loader = new RepositorySearchLoader(SearchActivity.this, null);
            loader.setQuery(mQuery);
            return loader;
        }

        @Override
        protected void onResultReady(List<Repository> result) {
            fillRepositoriesData(result);
        }
    };

    private final LoaderCallbacks<List<SearchUser>> mUserCallback = new LoaderCallbacks<List<SearchUser>>(this) {
        @Override
        protected Loader<LoaderResult<List<SearchUser>>> onCreateLoader() {
            return new UserSearchLoader(SearchActivity.this, mQuery);
        }

        @Override
        protected void onResultReady(List<SearchUser> result) {
            fillUsersData(result);
        }
    };

    private final LoaderCallbacks<List<CodeSearchResult>> mCodeCallback = new LoaderCallbacks<List<CodeSearchResult>>(this) {
        @Override
        protected Loader<LoaderResult<List<CodeSearchResult>>> onCreateLoader() {
            return new CodeSearchLoader(SearchActivity.this, mQuery);
        }

        @Override
        protected void onResultReady(List<CodeSearchResult> result) {
            fillCodeData(result);
        }

        @Override
        protected boolean onError(Exception e) {
             if (e instanceof RequestException) {
                RequestError error = ((RequestException) e).getError();
                if (error != null && error.getErrors() != null && !error.getErrors().isEmpty()) {
                    setEmptyText(R.string.code_search_too_broad);
                    setContentEmpty(true);
                    setContentShown(false);
                    return true;
                }
            }
            return super.onError(e);
        }
    };

    private LoaderManager.LoaderCallbacks<Cursor> mSuggestionCallback = new LoaderManager.LoaderCallbacks<Cursor>() {

        @Override
        public Loader<Cursor> onCreateLoader(int id, Bundle args) {
            return new CursorLoader(getApplicationContext(), SuggestionsProvider.Columns.CONTENT_URI,
                    new String[]{SuggestionsProvider.Columns._ID, SuggestionsProvider.Columns.SUGGESTION},
                    SuggestionsProvider.Columns.TYPE + " = ? and " + SuggestionsProvider.Columns.SUGGESTION + " like ?",
                    new String[]{String.valueOf(mSearchType.getSelectedItemPosition()), mQuery + "%"},
                    SuggestionsProvider.Columns.DATE + " desc");
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            mSearch.getSuggestionsAdapter().changeCursor(data);
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {
            mSearch.getSuggestionsAdapter().changeCursor(null);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.generic_list);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setTitle(R.string.search);

        LayoutInflater inflater = LayoutInflater.from(UiUtils.makeHeaderThemedContext(this));
        LinearLayout searchLayout = (LinearLayout) inflater.inflate(R.layout.search_action_bar, null);
        actionBar.setCustomView(searchLayout);
        actionBar.setDisplayShowCustomEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);

        mSearchType = (Spinner) searchLayout.findViewById(R.id.search_type);
        mSearchType.setAdapter(new SearchTypeAdapter(actionBar.getThemedContext(), this));
        mSearchType.setOnItemSelectedListener(this);

        mSearch = (SearchView) searchLayout.findViewById(R.id.search_view);
        mSearch.setIconifiedByDefault(true);
        mSearch.requestFocus();
        mSearch.setIconified(false);
        mSearch.setOnQueryTextListener(this);
        mSearch.setOnCloseListener(this);
        mSearch.onActionViewExpanded();

        mSearch.setOnSuggestionListener(this);
        mSearch.setSuggestionsAdapter(
                new SimpleCursorAdapter(this, android.R.layout.simple_list_item_1, null,
                        new String[]{SuggestionsProvider.Columns.SUGGESTION},
                        new int[]{android.R.id.text1}, 0)
        );

        getSupportLoaderManager().initLoader(1, null, mSuggestionCallback);

        updateSelectedSearchType();

        mResultsView = (RecyclerView) findViewById(R.id.list);
        mResultsView.setLayoutManager(new LinearLayoutManager(this));
        mResultsView.addItemDecoration(new DividerItemDecoration(this));

        if (savedInstanceState != null) {
            mQuery = savedInstanceState.getString(STATE_KEY_QUERY);
            mSearch.setQuery(mQuery, false);

            LoaderManager lm = getSupportLoaderManager();
            int previousMode = savedInstanceState.getInt(STATE_KEY_SEARCH_MODE, SEARCH_MODE_NONE);
            switch (previousMode) {
                case SEARCH_MODE_REPO: lm.initLoader(0, null, mRepoCallback); break;
                case SEARCH_MODE_USER: lm.initLoader(0, null, mUserCallback); break;
                case SEARCH_MODE_CODE: lm.initLoader(0, null, mCodeCallback); break;
            }
        }
    }

    @Override
    protected boolean canSwipeToRefresh() {
        // User can resubmit the query to restart the search
        return false;
    }

    @Override
    public void onRefresh() {
        Loader loader = getSupportLoaderManager().getLoader(0);
        if (loader != null) {
            if (mAdapter != null) {
                mAdapter.clear();
            }
            loader.onContentChanged();
        }
        super.onRefresh();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (mAdapter instanceof RepositoryAdapter) {
            outState.putInt(STATE_KEY_SEARCH_MODE, SEARCH_MODE_REPO);
        } else if (mAdapter instanceof SearchUserAdapter) {
            outState.putInt(STATE_KEY_SEARCH_MODE, SEARCH_MODE_USER);
        } else if (mAdapter instanceof CodeSearchAdapter) {
            outState.putInt(STATE_KEY_SEARCH_MODE, SEARCH_MODE_CODE);
        }
        outState.putString(STATE_KEY_QUERY, mQuery);
        super.onSaveInstanceState(outState);
    }

    private static class SearchTypeAdapter extends BaseAdapter implements SpinnerAdapter {
        private final Context mContext;
        private final LayoutInflater mInflater;
        private final LayoutInflater mPopupInflater;

        private final int[][] mResources = new int[][] {
            { R.string.search_type_repo, R.drawable.icon_repositories_dark, R.attr.searchRepoIcon, 0 },
            { R.string.search_type_user, R.drawable.search_users_dark, R.attr.searchUserIcon, 0 },
            { R.string.search_type_code, R.drawable.search_code_dark, R.attr.searchCodeIcon, 0 }
        };

        SearchTypeAdapter(Context context, Context popupContext) {
            mContext = context;
            mInflater = LayoutInflater.from(context);
            mPopupInflater = LayoutInflater.from(popupContext);
            for (int i = 0; i < mResources.length; i++) {
                mResources[i][3] = UiUtils.resolveDrawable(popupContext, mResources[i][2]);
            }
        }

        @Override
        public int getCount() {
            return mResources.length;
        }

        @Override
        public CharSequence getItem(int position) {
            return mContext.getString(mResources[position][0]);
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.search_type_small, null);
            }

            ImageView icon = (ImageView) convertView.findViewById(R.id.icon);
            icon.setImageResource(mResources[position][1]);

            return convertView;
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = mPopupInflater.inflate(R.layout.search_type_popup, null);
            }

            ImageView icon = (ImageView) convertView.findViewById(R.id.icon);
            icon.setImageResource(mResources[position][3]);

            TextView label = (TextView) convertView.findViewById(R.id.label);
            label.setText(mResources[position][0]);

            return convertView;
        }
    }

    private void fillRepositoriesData(List<Repository> repos) {
        RepositoryAdapter adapter = new RepositoryAdapter(this);
        adapter.addAll(repos);
        setAdapter(adapter);
    }

    private void fillUsersData(List<SearchUser> users) {
        SearchUserAdapter adapter = new SearchUserAdapter(this);
        adapter.addAll(users);
        setAdapter(adapter);
    }

    private void fillCodeData(List<CodeSearchResult> results) {
        CodeSearchAdapter adapter = new CodeSearchAdapter(this);
        adapter.addAll(results);
        setAdapter(adapter);
    }

    private void setAdapter(RootAdapter<?, ?> adapter) {
        adapter.setOnItemClickListener(this);
        mResultsView.setAdapter(adapter);
        mAdapter = adapter;
        setContentShown(true);
        setContentEmpty(adapter.getCount() == 0);
    }

    private void updateSelectedSearchType() {
        switch (mSearchType.getSelectedItemPosition()) {
            case 0:
                mSearch.setQueryHint(getString(R.string.search_hint_repo));
                setEmptyText(R.string.no_search_repos_found);
                break;
            case 1:
                mSearch.setQueryHint(getString(R.string.search_hint_user));
                setEmptyText(R.string.no_search_users_found);
                break;
            case 2:
                mSearch.setQueryHint(getString(R.string.search_hint_code));
                setEmptyText(R.string.no_search_code_found);
                break;
            default:
                mSearch.setQueryHint(null);
                setEmptyText(null);
                break;
        }
        mSearch.getSuggestionsAdapter().changeCursor(null);
        getSupportLoaderManager().restartLoader(1, null, mSuggestionCallback);
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        LoaderManager lm = getSupportLoaderManager();
        int type = mSearchType.getSelectedItemPosition();
        switch (type) {
            case 1: lm.restartLoader(0, null, mUserCallback); break;
            case 2: lm.restartLoader(0, null, mCodeCallback); break;
            default: lm.restartLoader(0, null, mRepoCallback); break;
        }
        mQuery = query;
        if (!StringUtils.isBlank(query)) {
            new SaveSearchSuggestionTask(query, type).schedule();
        }
        setContentShown(false);
        mSearch.clearFocus();
        return true;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        CursorAdapter cursorAdapter = mSearch.getSuggestionsAdapter();
        if (cursorAdapter != null) {
            Cursor cursor = cursorAdapter.getCursor();
            if (newText.startsWith(mQuery) && cursor != null && cursor.getCount() == 0) {
                // nothing found on previous query
            } else {
                mQuery = newText;
                cursorAdapter.changeCursor(null);
                getSupportLoaderManager().restartLoader(1, null, mSuggestionCallback);
            }
        }
        mQuery = newText;
        return true;
    }

    @Override
    public boolean onSuggestionSelect(int position) {
        return false;
    }

    @Override
    public boolean onSuggestionClick(int position) {
        Cursor cursor = mSearch.getSuggestionsAdapter().getCursor();
        if (cursor.moveToPosition(position)) {
            mQuery = cursor.getString(1);
            mSearch.setQuery(mQuery, false);
        }
        return true;
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        updateSelectedSearchType();
        if (getSupportLoaderManager().getLoader(0) != null) {
            onQueryTextSubmit(mQuery);
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        updateSelectedSearchType();
    }

    @Override
    public boolean onClose() {
        if (mAdapter != null) {
            mAdapter.clear();
        }
        mQuery = null;
        return true;
    }

    @Override
    public void onItemClick(Object item) {
        if (item instanceof Repository) {
            Repository repository = (Repository) item;
            startActivity(RepositoryActivity.makeIntent(this, repository));
        } else if (item instanceof CodeSearchResult) {
            CodeSearchResult result = (CodeSearchResult) item;
            Repository repo = result.getRepository();
            Uri uri = Uri.parse(result.getUrl());
            String ref = uri.getQueryParameter("ref");
            startActivity(FileViewerActivity.makeIntent(this,
                    repo.getOwner().getLogin(), repo.getName(), ref, result.getPath()));
        } else {
            SearchUser user = (SearchUser) item;
            startActivity(UserActivity.makeIntent(this, user.getLogin(), user.getName()));
        }
    }


    private class SaveSearchSuggestionTask extends BackgroundTask<Void> {
        private String mSuggestion;
        private int mType;

        public SaveSearchSuggestionTask(String suggestion, int type) {
            super(SearchActivity.this);
            mSuggestion = suggestion;
            mType = type;
        }

        @Override
        protected Void run() throws IOException {
            ContentValues cv = new ContentValues();
            cv.put(SuggestionsProvider.Columns.TYPE, mType);
            cv.put(SuggestionsProvider.Columns.SUGGESTION, mSuggestion);
            cv.put(SuggestionsProvider.Columns.DATE, System.currentTimeMillis());
            getContentResolver().insert(SuggestionsProvider.Columns.CONTENT_URI, cv);
            return null;
        }

        @Override
        protected void onSuccess(Void result) {
        }
    }
}
