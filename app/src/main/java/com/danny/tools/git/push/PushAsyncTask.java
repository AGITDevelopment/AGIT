package com.danny.tools.git.push;
import android.os.*;
import org.eclipse.jgit.api.errors.*;
import org.eclipse.jgit.api.*;
import com.danny.tools.git.repository.*;
import java.io.*;
import com.danny.tools.git.remote.*;
import org.eclipse.jgit.transport.*;
import android.net.*;
import android.app.*;
import org.eclipse.jgit.lib.*;
import android.content.*;
import com.danny.agit.*;
import android.widget.*;
import com.danny.tools.*;
import android.util.*;
import java.util.*;

public class PushAsyncTask extends AsyncTask<PushAsyncTask.Param, PushAsyncTask.Progress, PushAsyncTask.Result>
{
	private Activity activity;
	private boolean isProgressDialogEnabled;
	private ProgressDialog mProgressDialog;
	private onTaskFinishListener listener;
	
	public PushAsyncTask(Activity activity) {
		this.activity = activity;
	}
	
	public void setProgressDialogEnabled (boolean enabled) {
		isProgressDialogEnabled = enabled;
	}
	
	public void setOnTaskFinishListener(onTaskFinishListener listener) {
		this.listener = listener;
	}

	@Override
	protected void onPreExecute() {
		super.onPreExecute();
		
		if (!isProgressDialogEnabled)
			return;
		
		mProgressDialog = new ProgressDialog(activity, R.style.AppTheme_ProgressDialog_ColorAccent);
		
		mProgressDialog.setTitle(R.string.push);
		mProgressDialog.setMessage(activity.getString(R.string.push) + "...");
		mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		mProgressDialog.setCancelable(false);
		mProgressDialog.show();
	}

	@Override
	protected PushAsyncTask.Result doInBackground(PushAsyncTask.Param[] params) {
		// check connectivity status
		Result successResult = new Result(true);
		
		if (!checkConnectivity())
			return new Result(false, Result.ERR_RES_NO_CONNECTION);
		
		for (Param param: params) {
			try {
				Git git = new Git(RepositoryUtils.openRepository(param.path));
				String sUrl = RemoteUtils.getRemoteUrl(param.path, param.name);
				PushCommand pushCommand = git.push().setRemote(sUrl).setProgressMonitor(monitor);
				
				if (param.isPushAll)
					pushCommand = pushCommand.setPushAll();
				if (!param.isAuthIgnored)
					pushCommand = pushCommand.setCredentialsProvider(new UsernamePasswordCredentialsProvider(param.username, param.password));
				
				Iterable<PushResult> resultIterator = pushCommand.call();
				
				for (PushResult result: resultIterator) {
					Collection<RemoteRefUpdate> remoteUpdates = result.getRemoteUpdates();
					successResult.updateCollection = remoteUpdates;
				}
			} catch (IOException e) {
				e.printStackTrace();
				return new Result(false, e);
			} catch (GitAPIException e) {
				e.printStackTrace();
				return new Result(false, e);
			}
		}
		return successResult;
	}

	@Override
	protected void onProgressUpdate(PushAsyncTask.Progress[] progresses) {
		super.onProgressUpdate(progresses);
		
		if (!isProgressDialogEnabled)
			return;
		
		for (Progress progress: progresses) {
			switch (progress.type) {
				case Progress.PROGRESS_DEFAULT:
					mProgressDialog.setProgress(progress.completed);
					break;
				case Progress.PROGRESS_NEW_TASK:
					if (progress.totalWork == 0) {
						mProgressDialog.setIndeterminate(true);
						mProgressDialog.setMessage(progress.taskName);
						mProgressDialog.setMax(0);
					} else {
						mProgressDialog.setIndeterminate(false);
						mProgressDialog.setMessage(progress.taskName);
						mProgressDialog.setMax(progress.totalWork);
					}
					break;
			}
		}
	}

	@Override
	protected void onPostExecute(PushAsyncTask.Result result) {
		super.onPostExecute(result);
		
		if (isProgressDialogEnabled)
			mProgressDialog.dismiss();
		
		if (listener != null)
			listener.onTaskFinish(result);
		
		if (result.isSuccess) {
			StringBuilder builder = new StringBuilder();
			for (RemoteRefUpdate update: result.updateCollection) {
				builder.append(parseRemoteRefUpdate(update));
			}
			showResultDialog(builder.toString());
		}
		else {
			if (result.stringRes != 0)
				Toast.makeText(activity, result.message, Toast.LENGTH_LONG).show();
			else if (result.e != null)
				ExceptionUtils.toastException(activity, result.e);
			else if (result.message != null)
				Toast.makeText(activity, result.message, Toast.LENGTH_LONG).show();
			else
				ExceptionUtils.toastException(activity, NullPointerException.class);
		}
	}
	
	private ProgressMonitor monitor = new ProgressMonitor() {
		private int totalCompleted = 0;

		@Override
		public void start(int totalTasks) {
			
		}

		@Override
		public void beginTask(String title, int totalWorks) {
			publishProgress(new Progress(title, totalWorks));
			totalCompleted = 0;
		}

		@Override
		public void update(int completed) {
			totalCompleted += completed;
			publishProgress(new Progress(totalCompleted));
		}

		@Override
		public void endTask() {
		}

		@Override
		public boolean isCancelled() {
			return false;
		}
	};

	private boolean checkConnectivity() {
		ConnectivityManager cm = (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);

		NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
		boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();
		return isConnected;
	}
	
	private void showResultDialog(String message) {
		AlertDialog.Builder builder = new AlertDialog.Builder(activity, R.style.AppTheme_AlertDialog_ColorAccent);
		AlertDialog dialog = builder.setTitle(R.string.push)
			.setMessage(message)
			.setPositiveButton(android.R.string.ok, null)
			.create();
		dialog.show();
	}
	
	private String parseRemoteRefUpdate(RemoteRefUpdate update) {
        String msg = null;
        switch (update.getStatus()) {
            case AWAITING_REPORT:
				msg = String.format(activity.getString(R.string.push_result_awaiting_report), update.getRemoteName());
                break;
            case NON_EXISTING:
				msg = String.format(activity.getString(R.string.push_result_non_existing), update.getRemoteName());
                break;
            case NOT_ATTEMPTED:
				msg = String.format(activity.getString(R.string.push_result_not_attempted), update.getRemoteName());
                break;
            case OK:
				msg = String.format(activity.getString(R.string.push_result_ok), update.getRemoteName());
                break;
            case REJECTED_NODELETE:
				msg = String.format(activity.getString(R.string.push_result_rejected_nodelete), update.getRemoteName());
                break;
            case REJECTED_NONFASTFORWARD:
				msg = String.format(activity.getString(R.string.push_result_rejected_nonfastforward), update.getRemoteName());
				break;
            case REJECTED_OTHER_REASON:
                String reason = update.getMessage();
                if (reason == null || reason.isEmpty()) {
					msg = String.format(activity.getString(R.string.push_result_rejected_other_reason), update.getRemoteName());
                } else {
					msg = String.format(activity.getString(R.string.push_reslut_rejected_other_message), update.getRemoteName(), reason);
                }
                break;
            case REJECTED_REMOTE_CHANGED:
				msg = String.format(activity.getString(R.string.push_result_rejected_remote_changed), update.getRemoteName());
                break;
            case UP_TO_DATE:
				msg = String.format(activity.getString(R.string.push_result_up_to_date), update.getRemoteName());
                break;
        }
        return msg;
    }
	
	public interface onTaskFinishListener {
		public void onTaskFinish(Result result);
	}
	
	public static class Param {
		private String path;
		private String name;
		private String username;
		private String password;
		private boolean isPushAll;
		private boolean isAuthIgnored;
		
		public Param() {
			
		}

		public Param(String path, String url, String username, String password, boolean isPushAll, boolean isAuthIgnored)
		{
			this.path = path;
			this.name = url;
			this.username = username;
			this.password = password;
			this.isPushAll = isPushAll;
			this.isAuthIgnored = isAuthIgnored;
		}

		public void setIsPushAll(boolean isPushAll)
		{
			this.isPushAll = isPushAll;
		}

		public boolean isPushAll()
		{
			return isPushAll;
		}

		public void setIsAuthIgnored(boolean isAuthIgnored)
		{
			this.isAuthIgnored = isAuthIgnored;
		}

		public boolean isAuthIgnored()
		{
			return isAuthIgnored;
		}

		public void setUrl(String url)
		{
			this.name = url;
		}

		public String getUrl()
		{
			return name;
		}

		public void setPath(String path)
		{
			this.path = path;
		}

		public String getPath()
		{
			return path;
		}

		public void setUsername(String username)
		{
			this.username = username;
		}

		public String getUsername()
		{
			return username;
		}

		public void setPassword(String password)
		{
			this.password = password;
		}

		public String getPassword()
		{
			return password;
		}
	}
	
	static class Progress {
		static final int PROGRESS_DEFAULT = 1;
		static final int PROGRESS_NEW_TASK = 2;
		
		int type;
		String taskName;
		int totalWork;
		int completed;
		
		Progress() {
			
		}

		Progress(int completed) {
			this.type = PROGRESS_DEFAULT;
			this.completed = completed;
		}

		Progress(String taskName, int totalWork) {
			this.type = PROGRESS_NEW_TASK;
			this.taskName = taskName;
			this.totalWork = totalWork;
		}
	}
	
	public class Result {
		public static final int ERR_RES_NO_CONNECTION = R.string.err_no_internet;
		
		private boolean isSuccess;
		private Collection<RemoteRefUpdate> updateCollection;
		private String message;
		private int stringRes;
		private Exception e;
		
		Result(boolean isSuccess) {
			this.isSuccess = isSuccess;
		}

		Result(boolean isSuccess, String message) {
			this.isSuccess = isSuccess;
			this.message = message;
		}
		
		Result (boolean isSuccess, int stringRes) {
			this.isSuccess = isSuccess;
			this.stringRes = stringRes;
			this.message = activity.getString(stringRes);
		}
		
		Result (boolean isSuccess, Exception e) {
			this.isSuccess = isSuccess;
			this.e = e;
			this.message = e.getClass().getName();
		}
		
		public boolean isSuccess() {
			return isSuccess;
		}
		
		public String getMessage() {
			return message;
		}
	}
}
