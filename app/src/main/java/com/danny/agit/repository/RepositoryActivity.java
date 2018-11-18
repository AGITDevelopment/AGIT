package com.danny.agit.repository;
import android.support.v7.app.*;
import android.os.*;
import com.danny.agit.*;
import android.content.*;
import android.support.v7.widget.*;
import android.support.v4.app.*;
import android.support.v7.app.ActionBar.*;
import android.support.design.widget.*;
import android.support.v4.view.*;
import android.support.design.widget.TabLayout.*;
import java.util.*;
import android.view.*;
import android.content.res.*;
import android.util.*;
import org.eclipse.jgit.revwalk.*;
import com.danny.tools.git.commit.*;
import org.eclipse.jgit.api.errors.*;
import com.danny.tools.*;
import android.widget.ImageView;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.lib.*;
import com.danny.tools.git.remote.*;
import java.io.*;
import com.danny.tools.git.push.*;
import com.danny.tools.git.push.PushAsyncTask.*;
import android.widget.Toast;
import com.danny.tools.data.auth.*;
import com.danny.tools.git.gitignore.*;
import com.danny.tools.git.fetch.*;
import com.danny.tools.git.fetch.FetchAsyncTask.*;

public class RepositoryActivity extends AppCompatActivity implements PushDialog.OnOkClickListener, AuthDialog.OnOkClickListener, AddRemoteDialog.OnOkClickListener, AddLanguageDialog.OnReceiveListener, FetchDialog.OnReceiveListener
{
	public static final String PARAM_NAME = "NAME";
	public static final String PARAM_PATH = "PATH";
	
	private String paramName;
	private String paramPath;
	private Operation currentOperation; 
	
	// auth
	private boolean isAuthSaved;
	private boolean isAuthIgnored;
	
	// push
	private String sPushName;
	private boolean isPushAll;
	private String sPushUsername;
	private String sPushPassword;
	
	// fetch
	private String sFetchName;
	private String sFetchUsername;
	private String sFetchPassword;
	
	private TabLayout mTabLayout;
	private ViewPager mViewPager;
	private RepositoryPagerAdapter adapter;
	private FloatingActionButton mFabCommit;
	private ImageView mImgRemote, mImgBranch;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		// TODO: Implement this method
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_repository);
		
		// init views
		mTabLayout = findViewById(R.id.tabLayout);
		mViewPager = findViewById(R.id.repositoryViewPager);
		mFabCommit = findViewById(R.id.repositoryFab);
		mImgRemote = findViewById(R.id.repositoryImgRemote);
		mImgBranch = findViewById(R.id.repositoryImgBranch);
		
		// get extras
		Intent it = getIntent();
		Bundle bundle = it.getExtras();
		paramName = bundle.getString(PARAM_NAME);
		paramPath = bundle.getString(PARAM_PATH);
		
		// init toolbar
		Toolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		ActionBar actionBar = getSupportActionBar();
		if (paramName != null)
			actionBar.setTitle(paramName);
		
		// init file fragment
		RepositoryFileFragment fileFragment = new RepositoryFileFragment();
		Bundle args = new Bundle();
		args.putString(RepositoryFileFragment.ARG_PATH, paramPath);
		fileFragment.setArguments(args);
		
		// init commit fragment
		RepositoryCommitFragment commitFragment = new RepositoryCommitFragment();
		Bundle args2 = new Bundle();
		args2.putString(RepositoryCommitFragment.ARG_PATH, paramPath);
		commitFragment.setArguments(args2);
			
		// init view pager
		ArrayList<Fragment> fragmentList = new ArrayList<>();
		fragmentList.add(fileFragment);
		fragmentList.add(commitFragment);
		// get commit count
		int count = 0;
		try {
			List<RevCommit> commitList = LogUtils.getAllLogsInverse(paramPath);
			count = commitList.size();
		} catch (GitAPIException e) {
			ExceptionUtils.toastException(RepositoryActivity.this, e);
		}
		// init title
		ArrayList<String> titleList = new ArrayList<>();
		titleList.add(getString(R.string.repository_tab_title_file));
		titleList.add(getString(R.string.repository_tab_title_commit, count));
		adapter = new RepositoryPagerAdapter(getSupportFragmentManager(), fragmentList);
		adapter.setTitleList(titleList);
		mViewPager.setAdapter(adapter);
		mViewPager.addOnPageChangeListener(onPageChange);
		
		// init tab layout
		mTabLayout.setupWithViewPager(mViewPager);
		
		// init tab listener
		mTabLayout.addOnTabSelectedListener(onTabSelectedListener);
		mViewPager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(mTabLayout));
		
		// init listener
		mFabCommit.setOnClickListener(onFabCommitClick);
		mImgRemote.setOnClickListener(onImgRemoteClick);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		getMenuInflater().inflate(R.menu.activity_repository_file, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		// TODO: Implement this method
		int id = item.getItemId();
		
		switch(id) {
			case R.id.database:
				AuthRecordDao authDao = new AuthRecordDao(RepositoryActivity.this);
				authDao.copyDbTo("/storage/emulated/0/AGIT/debug/database");
				return true;
			case R.id.addLanguage:
				AddLanguageDialog languageDialog = new AddLanguageDialog();
				languageDialog.show(getSupportFragmentManager(), AddLanguageDialog.TAG);
				return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onBackPressed() {
		int position = mViewPager.getCurrentItem();
		Fragment selectedFragment = adapter.getItem(position);
		if (selectedFragment instanceof RepositoryFileFragment) {
			RepositoryFileFragment fileFragment = (RepositoryFileFragment) selectedFragment;
			if (!fileFragment.onBackPressed())
				super.onBackPressed();
		} else
			super.onBackPressed();
	}

	@Override
	public void onGetAddRemote(String name, String url) {
		if (RemoteUtils.getRemoteUrl(paramPath, name) == null) {
			try {
				RemoteUtils.remoteAdd(paramPath, name, url);
			} catch (IOException e) {
				ExceptionUtils.toastException(RepositoryActivity.this, e);
			}
		}
		
	}

	@Override
	public void onGetPushData(String name, boolean isPushAll) {
		this.sPushName = name;
		this.isPushAll = isPushAll;
		currentOperation = Operation.PUSH;
		
		AuthRecordDao authRecordDao = new AuthRecordDao(RepositoryActivity.this);
		AuthRecord authRecord = authRecordDao.get(paramName, sPushName);
		authRecordDao.close();
		
		if (authRecord == null) {
			AuthDialog authDialog = new AuthDialog();
			authDialog.show(getSupportFragmentManager(), AuthDialog.TAG);
		} else {
			sPushUsername = authRecord.getUserName();
			sPushPassword = authRecord.getPassword();
			isAuthIgnored = authRecord.isIgnored();
			push();
		}
	}

	@Override
	public void onGetPushAuthData(String username, String password, boolean isSaved, boolean isIgnored) {
		// set data
		this.isAuthSaved = isSaved;
		this.isAuthIgnored = isIgnored;
		
		switch (currentOperation) {
			case PUSH:
				this.sPushUsername = username;
				this.sPushPassword = password;
				push();
				break;
			case FETCH:
				this.sFetchUsername = username;
				this.sPushPassword = password;
				fetch();
				break;
		}
	}

	@Override
	public void onLanguageReceive(String language) {
		GitignoreManager manager = new GitignoreManager(RepositoryActivity.this);
		File dest = new File(paramPath + File.separator + GitignoreManager.EXTENTION);
		
		if (dest.exists()) {
			Toast.makeText(RepositoryActivity.this, R.string.gitignore_exists, Toast.LENGTH_LONG).show();
			return;
		}
		
		manager.copyGitignoreTo(language, dest.toString());
		Toast.makeText(RepositoryActivity.this, R.string.gitignore_successfully_created, Toast.LENGTH_SHORT).show();
		Fragment fragment = adapter.getItem(0);
		if (fragment instanceof RepositoryFileFragment) {
			RepositoryFileFragment fileFragment = (RepositoryFileFragment) fragment;
			fileFragment.refreshFileList();
		}
	}

	@Override
	public void onFetchReceive(String remoteName) {
		sFetchName = remoteName;
		currentOperation = Operation.FETCH;
		
		AuthRecordDao authRecordDao = new AuthRecordDao(RepositoryActivity.this);
		AuthRecord authRecord = authRecordDao.get(paramName, sFetchName);
		authRecordDao.close();

		if (authRecord == null) {
			AuthDialog authDialog = new AuthDialog();
			authDialog.show(getSupportFragmentManager(), AuthDialog.TAG);
		} else {
			sFetchUsername = authRecord.getUserName();
			sFetchPassword = authRecord.getPassword();
			isAuthIgnored = authRecord.isIgnored();
			fetch();
		}
	}
	
	private View.OnClickListener onFabCommitClick = new View.OnClickListener() {
		@Override
		public void onClick(View view) {
			Intent it = new Intent();
			Bundle params = new Bundle();
			params.putString(RepositoryCommitActivity.PARAM_KEY_PATH, paramPath);
			it.setClass(RepositoryActivity.this, RepositoryCommitActivity.class);
			it.putExtras(params);
			startActivity(it);
		}
	};
	
	private View.OnClickListener onImgRemoteClick = new View.OnClickListener() {
		@Override
		public void onClick(View view) {
			PopupMenu popupMenu = new PopupMenu(RepositoryActivity.this, mImgRemote);
			popupMenu.inflate(R.menu.repository_cloud);
			popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
				@Override
				public boolean onMenuItemClick(MenuItem item)
				{
					switch (item.getItemId()) {
						case R.id.push:
							PushDialog pushDialog = new PushDialog();
							Bundle args = new Bundle();
							args.putString(PushDialog.ARG_KEY_PATH, paramPath);
							pushDialog.setArguments(args);
							pushDialog.show(getSupportFragmentManager(), PushDialog.TAG);
							return true;
						case R.id.addRemote:
							AddRemoteDialog addRemoteDialog = new AddRemoteDialog();
							addRemoteDialog.show(getSupportFragmentManager(), AddRemoteDialog.TAG);
							return true;
						case R.id.fetch:
							FetchDialog fetchDialog = new FetchDialog();
							Bundle args2 = new Bundle();
							args2.putString(FetchDialog.ARG_KEY_PATH, paramPath);
							fetchDialog.setArguments(args2);
							fetchDialog.show(getSupportFragmentManager(), FetchDialog.TAG);
							return true;
					}
					return false;
				}
			});
			popupMenu.show();
		}
	};
	
	private PushAsyncTask.onTaskFinishListener onPushFinish = new PushAsyncTask.onTaskFinishListener() {
		@Override
		public void onTaskFinish(PushAsyncTask.Result result){
			if (result.isSuccess() && isAuthSaved) {
				AuthRecordDao authRecordDao = new AuthRecordDao(RepositoryActivity.this);
				if (authRecordDao.get(paramName, sPushName) == null) {
					AuthRecord auth = new AuthRecord(paramName, sPushName, sPushUsername, sPushPassword, isAuthIgnored);
					auth = authRecordDao.insert(auth);
				}
				authRecordDao.close();
			}
		}
	};
	
	private FetchAsyncTask.onTaskFinishListener onFetchFinish = new FetchAsyncTask.onTaskFinishListener() {
		@Override
		public void onTaskFinish(FetchAsyncTask.Result result) {
			if (result.isSuccess() && isAuthSaved) {
				AuthRecordDao authRecordDao = new AuthRecordDao(RepositoryActivity.this);
				if (authRecordDao.get(paramName, sPushName) == null) {
					AuthRecord auth = new AuthRecord(paramName, sPushName, sFetchUsername, sFetchPassword, isAuthIgnored);
					auth = authRecordDao.insert(auth);
				}
				authRecordDao.close();
			}
		}
	};
	
	private TabLayout.OnTabSelectedListener onTabSelectedListener = new TabLayout.OnTabSelectedListener() {

		@Override
		public void onTabSelected(TabLayout.Tab tab) {
			// TODO: Implement this method
			mViewPager.setCurrentItem(tab.getPosition());
		}

		@Override
		public void onTabUnselected(TabLayout.Tab p1) {
			// TODO: Implement this method
		}

		@Override
		public void onTabReselected(TabLayout.Tab p1) {
			// TODO: Implement this method
		}
	};
	
	private ViewPager.OnPageChangeListener onPageChange = new ViewPager.OnPageChangeListener() {

		@Override
		public void onPageScrolled(int column, float columnOffSet, int columnOffSetPixels) {
			// TODO: Implement this method
		}

		@Override
		public void onPageSelected(int column) {
			switch (column) {
				case 0:
					mFabCommit.setVisibility(View.VISIBLE);
					break;
				case 1:
					mFabCommit.setVisibility(View.GONE);
					break;
			}
		}

		@Override
		public void onPageScrollStateChanged(int state) {
			// TODO: Implement this method
		}
	};
	
	private void push() {
		PushAsyncTask pushTask = new PushAsyncTask(RepositoryActivity.this);
		PushAsyncTask.Param param = new PushAsyncTask.Param(paramPath, sPushName, sPushUsername, sPushPassword, isPushAll, isAuthIgnored);
		pushTask.setProgressDialogEnabled(true);
		pushTask.setOnTaskFinishListener(onPushFinish);
		pushTask.execute(new PushAsyncTask.Param[]{param});
	}
	
	private void fetch() {
		FetchAsyncTask fetchTask = new FetchAsyncTask(RepositoryActivity.this);
		FetchAsyncTask.Param param = new FetchAsyncTask.Param(paramPath, sFetchName, sFetchUsername, sFetchPassword, isAuthIgnored);
		fetchTask.setProgressDialogEnabled(true);
		fetchTask.setOnTaskFinishListener(onFetchFinish);
		fetchTask.execute(new FetchAsyncTask.Param[]{param});
	}
	
	private enum Operation {
		PUSH, FETCH;
	}
}
