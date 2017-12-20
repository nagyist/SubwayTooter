package jp.juggler.subwaytooter.api_tootsearch.entity;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;

import jp.juggler.subwaytooter.api.entity.CustomEmoji;
import jp.juggler.subwaytooter.api.entity.NicoProfileEmoji;
import jp.juggler.subwaytooter.api.entity.TootAttachment;
import jp.juggler.subwaytooter.api.entity.TootStatus;
import jp.juggler.subwaytooter.api.entity.TootStatusLike;
import jp.juggler.subwaytooter.api_tootsearch.TootsearchClient;
import jp.juggler.subwaytooter.table.SavedAccount;
import jp.juggler.subwaytooter.util.DecodeOptions;
import jp.juggler.subwaytooter.util.LogCategory;
import jp.juggler.subwaytooter.util.Utils;
import jp.juggler.subwaytooter.util.WordTrieTree;

public class TSToot extends TootStatusLike {
	
	private static final LogCategory log = new LogCategory( "TSToot" );
	
	private String created_at;
	
	@Nullable public TootAttachment.List media_attachments;
	
	// A Fediverse-unique resource ID
	public String uri;
	
	@Nullable
	private static TSToot parse( @NonNull Context context, SavedAccount access_info, JSONObject src ){
		if( src == null ) return null;
		TSToot dst = new TSToot();
		
		dst.account = TSAccount.parseAccount( context, access_info, src.optJSONObject( "account" ) );
		if( dst.account == null ){
			log.e( "missing status account" );
			return null;
		}
		
		dst.json = src;
		
		
		// 絵文字マップは割と最初の方で読み込んでおきたい
		dst.custom_emojis = CustomEmoji.parseMap( src.optJSONArray( "emojis" ),access_info.host);
		dst.profile_emojis = NicoProfileEmoji.parseMap( src.optJSONArray( "profile_emojis" ) );
		
		dst.url = Utils.optStringX( src, "url" );
		dst.uri = Utils.optStringX( src, "uri" );
		dst.host_original = dst.account.getAcctHost();
		dst.host_access = "?";
		dst.id = -1L; // Utils.optLongX( src, "id", - 1L );
		
		if( TextUtils.isEmpty( dst.url ) || TextUtils.isEmpty( dst.host_original ) ){
			log.e( "missing status url or host or id" );
			return null;
		}
		
		dst.created_at = Utils.optStringX( src, "created_at" );
		dst.time_created_at = TootStatus.parseTime( dst.created_at );
		
		dst.media_attachments = TootAttachment.parseList( src.optJSONArray( "media_attachments" ) );
		
		dst.sensitive = src.optBoolean( "sensitive" ,false );
		
		dst.setSpoilerText( context, Utils.optStringX( src, "spoiler_text" ) );
		
		dst.content = Utils.optStringX( src, "content" );
		dst.decoded_content = new DecodeOptions()
			.setShort( true )
			.setDecodeEmoji( true )
			.setCustomEmojiMap( dst.custom_emojis )
			.setProfileEmojis( dst.profile_emojis )
			.setLinkTag( dst )
			.decodeHTML( context, access_info, dst.content );
		
		return dst;
	}
	
	public static class List extends ArrayList< TSToot > {
	}
	
	@NonNull public static TSToot.List parseList( @NonNull Context context, SavedAccount access_info, @NonNull JSONObject root ){
		TSToot.List list = new TSToot.List();
		JSONArray array = TootsearchClient.getHits( root );
		if( array != null ){
			for( int i = 0, ie = array.length() ; i < ie ; ++ i ){
				JSONObject src = array.optJSONObject( i );
				if( src == null ) continue;
				JSONObject src2 = src.optJSONObject( "_source" );
				TSToot item = parse( context, access_info, src2 );
				if( item == null ) continue;
				list.add( item );
			}
		}
		return list;
	}
	
	public boolean checkMuted( @SuppressWarnings("UnusedParameters") @NonNull HashSet< String > muted_app, @NonNull WordTrieTree muted_word ){
		
		// word mute
		if( decoded_content != null && muted_word.containsWord( decoded_content.toString() ) ){
			return true;
		}
		
		if( decoded_spoiler_text != null && muted_word.containsWord( decoded_spoiler_text.toString() ) ){
			return true;
		}
		
		//		// reblog
		//		return reblog != null && reblog.checkMuted( muted_app, muted_word );
		
		return false;
		
	}
	
	public boolean hasMedia(){
		return media_attachments != null && media_attachments.size() > 0;
	}
	
	@Override public boolean canPin( SavedAccount access_info ){
		return false;
	}
}
