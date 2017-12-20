package jp.juggler.subwaytooter;

import android.app.SearchManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;

import java.util.Locale;

import jp.juggler.subwaytooter.api.entity.TootAccount;
import jp.juggler.subwaytooter.api.entity.TootAttachment;
import jp.juggler.subwaytooter.api.entity.TootStatus;
import jp.juggler.subwaytooter.api.entity.TootStatusLike;
import jp.juggler.subwaytooter.api_msp.entity.MSPAccount;
import jp.juggler.subwaytooter.api_msp.entity.MSPToot;
import jp.juggler.subwaytooter.api_tootsearch.entity.TSToot;
import jp.juggler.subwaytooter.table.MutedWord;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.util.DecodeOptions;
import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;

public class ActText extends AppCompatActivity implements View.OnClickListener {
	
	static final LogCategory log = new LogCategory( "ActText" );
	
	static final String EXTRA_TEXT = "text";
	static final String EXTRA_CONTENT_START = "content_start";
	static final String EXTRA_CONTENT_END = "content_end";
	
	static void addAfterLine( StringBuilder sb, @NonNull String text ){
		if( sb.length() > 0 && sb.charAt( sb.length() - 1 ) != '\n' ){
			sb.append( '\n' );
		}
		sb.append( text );
	}
	
	static void addHeader( Context context, StringBuilder sb, int key_str_id, Object value ){
		if( sb.length() > 0 && sb.charAt( sb.length() - 1 ) != '\n' ){
			sb.append( '\n' );
		}
		addAfterLine( sb, context.getString( key_str_id ) );
		sb.append( ": " );
		sb.append( value == null ? "(null)" : value.toString() );
	}
	
	static void encodeStatus( Intent intent, Context context, SavedAccount access_info, @NonNull TootStatusLike status ){
		StringBuilder sb = new StringBuilder();
		
		addHeader( context, sb, R.string.send_header_url, status.url );
		
		addHeader( context, sb, R.string.send_header_date, TootStatus.formatTime( context, status.time_created_at, false ) );
		
		if( status.account != null ){
			addHeader( context, sb, R.string.send_header_from_acct, access_info.getFullAcct( status.account ) );
			
			addHeader( context, sb, R.string.send_header_from_name, status.account.display_name );
		}
		
		if( ! TextUtils.isEmpty( status.spoiler_text ) ){
			addHeader( context, sb, R.string.send_header_content_warning, status.spoiler_text );
		}
		
		addAfterLine( sb, "\n" );
		
		intent.putExtra( EXTRA_CONTENT_START, sb.length() );
		sb.append( new DecodeOptions().decodeHTML( context, access_info, status.content ) );
		intent.putExtra( EXTRA_CONTENT_END, sb.length() );
		
		if( status instanceof TootStatus ){
			dumpAttachment( sb, ( (TootStatus) status ).media_attachments );
		}else if( status instanceof TSToot ){
			dumpAttachment( sb, ( (TSToot) status ).media_attachments );
		}else if( status instanceof MSPToot ){
			MSPToot ts = (MSPToot) status;
			if( ts.media_attachments != null ){
				int i = 0;
				for( String ma : ts.media_attachments ){
					++ i;
					addAfterLine( sb, "\n" );
					addAfterLine( sb, String.format( Locale.JAPAN, "Media-%d-Preview-Url: %s", i, ma ) );
				}
			}
		}
		
		addAfterLine( sb, String.format( Locale.JAPAN, "Status-Source: %s", status.json ) );
		
		addAfterLine( sb, "" );
		intent.putExtra( EXTRA_TEXT, sb.toString() );
	}
	
	static void dumpAttachment( @NonNull StringBuilder sb, @Nullable TootAttachment.List src ){
		if( src == null ) return;
		int i = 0;
		for( TootAttachment ma : src ){
			++ i;
			addAfterLine( sb, "\n" );
			addAfterLine( sb, String.format( Locale.JAPAN, "Media-%d-Url: %s", i, ma.url ) );
			addAfterLine( sb, String.format( Locale.JAPAN, "Media-%d-Remote-Url: %s", i, ma.remote_url ) );
			addAfterLine( sb, String.format( Locale.JAPAN, "Media-%d-Preview-Url: %s", i, ma.preview_url ) );
			addAfterLine( sb, String.format( Locale.JAPAN, "Media-%d-Text-Url: %s", i, ma.text_url ) );
		}
	}
	
	static void encodeAccount( Intent intent, Context context, SavedAccount access_info, @NonNull TootAccount who ){
		StringBuilder sb = new StringBuilder();
		
		intent.putExtra( EXTRA_CONTENT_START, sb.length() );
		sb.append( who.display_name );
		sb.append( "\n" );
		sb.append( "@" );
		sb.append( access_info.getFullAcct( who ) );
		sb.append( "\n" );
		
		intent.putExtra( EXTRA_CONTENT_START, sb.length() );
		sb.append( who.url );
		intent.putExtra( EXTRA_CONTENT_END, sb.length() );
		
		addAfterLine( sb, "\n" );
		
		sb.append( new DecodeOptions().decodeHTML( context, access_info, who.note ) );
		
		addAfterLine( sb, "\n" );
		
		addHeader( context, sb, R.string.send_header_account_name, who.display_name );
		addHeader( context, sb, R.string.send_header_account_acct, access_info.getFullAcct( who ) );
		addHeader( context, sb, R.string.send_header_account_url, who.url );
		
		addHeader( context, sb, R.string.send_header_account_image_avatar, who.avatar );
		addHeader( context, sb, R.string.send_header_account_image_avatar_static, who.avatar_static );
		addHeader( context, sb, R.string.send_header_account_image_header, who.header );
		addHeader( context, sb, R.string.send_header_account_image_header_static, who.header_static );
		
		if( who instanceof MSPAccount ){
			// 検索結果の場合、以下のパラメータは出力しない
		}else{
			addHeader( context, sb, R.string.send_header_account_created_at, who.created_at );
			addHeader( context, sb, R.string.send_header_account_statuses_count, who.statuses_count );
			addHeader( context, sb, R.string.send_header_account_followers_count, who.followers_count );
			addHeader( context, sb, R.string.send_header_account_following_count, who.following_count );
			addHeader( context, sb, R.string.send_header_account_locked, who.locked );
		}
		
		addAfterLine( sb, "" );
		intent.putExtra( EXTRA_TEXT, sb.toString() );
	}
	
	public static void open( ActMain activity, int request_code, SavedAccount access_info, @NonNull TootStatusLike status ){
		Intent intent = new Intent( activity, ActText.class );
		encodeStatus( intent, activity, access_info, status );
		
		activity.startActivityForResult( intent, request_code );
	}
	
	public static void open( ActMain activity, int request_code, SavedAccount access_info, @NonNull TootAccount who ){
		Intent intent = new Intent( activity, ActText.class );
		encodeAccount( intent, activity, access_info, who );
		
		activity.startActivityForResult( intent, request_code );
	}
	
	@Override protected void onCreate( @Nullable Bundle savedInstanceState ){
		super.onCreate( savedInstanceState );
		App1.setActivityTheme( this, false );
		initUI();
		
		if( savedInstanceState == null ){
			Intent intent = getIntent();
			String sv = intent.getStringExtra( EXTRA_TEXT );
			int content_start = intent.getIntExtra( EXTRA_CONTENT_START, 0 );
			int content_end = intent.getIntExtra( EXTRA_CONTENT_END, sv.length() );
			etText.setText( sv );
			etText.setSelection( content_start, content_end );
		}
	}
	
	EditText etText;
	
	void initUI(){
		setContentView( R.layout.act_text );
		
		Styler.fixHorizontalMargin( findViewById( R.id.svFooterBar ) );
		Styler.fixHorizontalMargin( findViewById( R.id.svContent ) );
		
		etText = findViewById( R.id.etText );
		
		findViewById( R.id.btnCopy ).setOnClickListener( this );
		findViewById( R.id.btnSearch ).setOnClickListener( this );
		findViewById( R.id.btnSend ).setOnClickListener( this );
		findViewById( R.id.btnMuteWord ).setOnClickListener( this );
		findViewById( R.id.btnSearchMSP ).setOnClickListener( this );
		findViewById( R.id.btnSearchTS ).setOnClickListener( this );
	}
	
	@Override public void onClick( View v ){
		switch( v.getId() ){
		
		case R.id.btnCopy:
			copy();
			break;
		
		case R.id.btnSearch:
			search();
			break;
		
		case R.id.btnSend:
			send();
			break;
		
		case R.id.btnMuteWord:
			muteWord();
			break;
		
		case R.id.btnSearchMSP:
			searchToot( RESULT_SEARCH_MSP );
			break;
		
		case R.id.btnSearchTS:
			searchToot( RESULT_SEARCH_TS );
			break;
		}
	}
	
	private String getSelection(){
		int s = etText.getSelectionStart();
		int e = etText.getSelectionEnd();
		String text = etText.getText().toString();
		if( s == e ){
			return text;
		}else{
			return text.substring( s, e );
		}
	}
	
	private void copy(){
		try{
			// Gets a handle to the clipboard service.
			ClipboardManager clipboard = (ClipboardManager) getSystemService( Context.CLIPBOARD_SERVICE );
			
			// Creates a new text clip to put on the clipboard
			ClipData clip = ClipData.newPlainText( "text", getSelection() );
			
			// Set the clipboard's primary clip.
			//noinspection ConstantConditions
			clipboard.setPrimaryClip( clip );
			
			Utils.showToast( this, false, R.string.copy_complete );
		}catch( Throwable ex ){
			log.trace( ex );
			Utils.showToast( this, ex, "copy failed." );
		}
	}
	
	private void send(){
		try{
			
			Intent intent = new Intent();
			intent.setAction( Intent.ACTION_SEND );
			intent.setType( "text/plain" );
			intent.putExtra( Intent.EXTRA_TEXT, getSelection() );
			startActivity( intent );
			
		}catch( Throwable ex ){
			log.trace( ex );
			Utils.showToast( this, ex, "send failed." );
		}
	}
	
	private void search(){
		String sv = getSelection();
		if( TextUtils.isEmpty( sv ) ){
			Utils.showToast( this, false, "please select search keyword" );
			return;
		}
		try{
			Intent intent = new Intent( Intent.ACTION_WEB_SEARCH );
			intent.putExtra( SearchManager.QUERY, sv );
			if( intent.resolveActivity( getPackageManager() ) != null ){
				startActivity( intent );
			}
		}catch( Throwable ex ){
			log.trace( ex );
			Utils.showToast( this, ex, "search failed." );
		}
		
	}
	
	static final int RESULT_SEARCH_MSP = RESULT_FIRST_USER + 1;
	static final int RESULT_SEARCH_TS = RESULT_FIRST_USER + 2;
	
	private void searchToot( int resultCode ){
		String sv = getSelection();
		if( TextUtils.isEmpty( sv ) ){
			Utils.showToast( this, false, "please select search keyword" );
			return;
		}
		try{
			Intent data = new Intent();
			data.putExtra( Intent.EXTRA_TEXT, sv );
			setResult( resultCode, data );
			finish();
		}catch( Throwable ex ){
			log.trace( ex );
		}
	}
	
	private void muteWord(){
		try{
			MutedWord.save( getSelection() );
			for( Column column : App1.getAppState( this ).column_list ){
				column.removeMuteApp();
			}
			Utils.showToast( this, false, R.string.word_was_muted );
		}catch( Throwable ex ){
			log.trace( ex );
			Utils.showToast( this, ex, "muteWord failed." );
		}
	}
	
}
