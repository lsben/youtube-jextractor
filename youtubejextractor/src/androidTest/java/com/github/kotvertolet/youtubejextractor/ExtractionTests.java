package com.github.kotvertolet.youtubejextractor;

import android.os.Bundle;
import android.support.test.runner.AndroidJUnit4;

import com.github.kotvertolet.youtubejextractor.exception.ExtractionException;
import com.github.kotvertolet.youtubejextractor.exception.YoutubeRequestException;
import com.github.kotvertolet.youtubejextractor.models.AdaptiveAudioStream;
import com.github.kotvertolet.youtubejextractor.models.AdaptiveVideoStream;
import com.github.kotvertolet.youtubejextractor.models.youtube.playerResponse.MuxedStream;
import com.github.kotvertolet.youtubejextractor.models.youtube.videoData.YoutubeVideoData;
import com.github.kotvertolet.youtubejextractor.network.YoutubeSiteNetwork;
import com.google.gson.GsonBuilder;

import junit.framework.TestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

import okhttp3.ResponseBody;
import retrofit2.Response;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

@RunWith(AndroidJUnit4.class)
public class ExtractionTests extends TestCase {
    private YoutubeJExtractor youtubeJExtractor = new YoutubeJExtractor();
    private YoutubeSiteNetwork youtubeSiteNetwork = new YoutubeSiteNetwork(new GsonBuilder().create());
    private YoutubeVideoData videoData;

    @Test(expected = ExtractionException.class)
    public void checkInvalidVideoId() throws YoutubeRequestException, ExtractionException {
        youtubeJExtractor.extract("invalid_id");
    }

    @Test
    public void checkVideoDataParcel() throws YoutubeRequestException, ExtractionException {
        String parcelKey = "parcel_key1";
        videoData = youtubeJExtractor.extract("rkas-NHQnsI");
        Bundle bundle = new Bundle();
        bundle.putParcelable(parcelKey, videoData);
        assertEquals(videoData, bundle.getParcelable(parcelKey));
    }

    @Test
    public void checkVideoWithEncryptedSignature() throws ExtractionException, YoutubeRequestException {
        videoData = youtubeJExtractor.extract("EztbyhAJNtk");
        checkIfStreamsWork(videoData);
    }

    @Test
    public void checkVideoWithoutEncryptedSignature() throws ExtractionException, YoutubeRequestException {
        videoData = youtubeJExtractor.extract("jNQXAC9IVRw");
        checkIfStreamsWork(videoData);
    }

    @Test
    public void checkVideoWithAgeCheck() throws ExtractionException, YoutubeRequestException {
        videoData = youtubeJExtractor.extract("h3yFGoSkgk8");
        checkIfStreamsWork(videoData);
    }

    @Test
    public void checkVeryLongVideo() throws ExtractionException, YoutubeRequestException {
        videoData = youtubeJExtractor.extract("85bkCmaOh4o");
        checkIfStreamsWork(videoData);
    }

    @Test
    public void checkVideoWithRestrictedEmbedding() throws ExtractionException, YoutubeRequestException {
        videoData = youtubeJExtractor.extract("XcicOBS9mBU");
        checkIfStreamsWork(videoData);
    }

    @Test
    public void checkLiveStream() throws YoutubeRequestException, ExtractionException {
        videoData = youtubeJExtractor.extract("5qap5aO4i9A");
        assertTrue(videoData.getVideoDetails().isLiveContent());
        assertNotNull(videoData.getStreamingData().getDashManifestUrl());
        assertNotNull(videoData.getStreamingData().getHlsManifestUrl());
        checkIfStreamsWork(videoData);
    }

//    @Test
//    public void checkLiveStreamWithoutAdaptiveStreams() throws YoutubeRequestException, ExtractionException {
//        videoData = youtubeJExtractor.extract("NMre6IAAAiU");
//        assertTrue(videoData.getVideoDetails().isLiveContent());
//        assertTrue(videoData.getStreamingData().getDashManifestUrl() != null
//                || videoData.getStreamingData().getHlsManifestUrl() != null);
//        assertEquals(0, videoData.getStreamingData().getAdaptiveAudioStreams().size());
//        assertEquals(0, videoData.getStreamingData().getAdaptiveVideoStreams().size());
//    }

    @Test
    public void checkMuxedStreamNonEncrypted() throws YoutubeRequestException, ExtractionException {
        videoData = youtubeJExtractor.extract("8QyDmvuts9s");
        checkIfStreamsWork(videoData);
    }

    @Test
    public void checkCallbackBasedExtractionSuccessful() {
        youtubeJExtractor.extract("iIKxyDRjecU", new JExtractorCallback() {
            @Override
            public void onSuccess(YoutubeVideoData videoData) {
                checkIfStreamsWork(videoData);
            }

            @Override
            public void onNetworkException(YoutubeRequestException e) {
                fail("Network exception occurred");
            }

            @Override
            public void onError(Exception exception) {
                fail("Extraction exception occurred");
            }
        });
    }

    private void checkIfStreamsWork(YoutubeVideoData videoData) {
        String streamErrorMask = "Stream wasn't processed correctly, stream details:\\n %s";
        Response<ResponseBody> responseBody;
        try {
            if (videoData.getVideoDetails().isLiveContent()) {
                responseBody = youtubeSiteNetwork.getStream(videoData.getStreamingData().getDashManifestUrl());
                assertNotNull(responseBody);
                assertTrue(responseBody.isSuccessful());
                responseBody = youtubeSiteNetwork.getStream(videoData.getStreamingData().getHlsManifestUrl());
                assertNotNull(responseBody);
                assertTrue(responseBody.isSuccessful());
            } else {
                for (AdaptiveVideoStream adaptiveVideoStream : videoData.getStreamingData().getAdaptiveVideoStreams()) {
                    responseBody = youtubeSiteNetwork.getStream(adaptiveVideoStream.getUrl());
                    assertThat(String.format(streamErrorMask, adaptiveVideoStream.toString()), responseBody, is(not(nullValue())));
                    assertThat(String.format(streamErrorMask, adaptiveVideoStream.toString()), responseBody.isSuccessful(), is(true));
                }
                for (AdaptiveAudioStream adaptiveAudioStream : videoData.getStreamingData().getAdaptiveAudioStreams()) {
                    responseBody = youtubeSiteNetwork.getStream(adaptiveAudioStream.getUrl());
                    assertThat(String.format(streamErrorMask, adaptiveAudioStream.toString()), responseBody, is(not(nullValue())));
                    assertThat(String.format(streamErrorMask, adaptiveAudioStream.toString()), responseBody.isSuccessful(), is(true));
                }
                for (MuxedStream muxedStream : videoData.getStreamingData().getMuxedStreams()) {
                    responseBody = youtubeSiteNetwork.getStream(muxedStream.getUrl());
                    assertThat(String.format(streamErrorMask, muxedStream.toString()), responseBody, is(not(nullValue())));
                    assertThat(String.format(streamErrorMask, muxedStream.toString()), responseBody.isSuccessful(), is(true));
                }
            }
        }
        catch (YoutubeRequestException e) {
            fail(e.getMessage());
        }
    }
}