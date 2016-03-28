package test;

import com.binoculars.nuclei.Domain;
import com.binoculars.nuclei.Nucleus;
import com.binoculars.future.Signal;
import com.binoculars.future.CompletableFuture;
import com.binoculars.future.Future;
import com.binoculars.future.Spore;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

public class ShowcaseNucleus extends Nucleus<ShowcaseNucleus> {

    volatile int syncState = 99;

    ArrayList<String> stuff = new ArrayList<>();

    public void $simpleAsyncCall(String argument) {
        stuff.add(argument);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    //
    // CompletableFutures, CompletableFutures, await, race, all
    //

    public Future<Integer> $indexOfCompletableFuture(String what) {
        return new CompletableFuture<>(stuff.indexOf(what));
    }

    public Future<Integer> $combineCompletableFuture0( Future<Integer> a, Future<Integer> b) {
        CompletableFuture<Integer> result = new CompletableFuture<>(); // unresolved

        // FIXME: try to make it just aresult -> b.then...
        a.then( (aresult, err) -> b.then( (bresult, err2) -> {
            result.complete( Math.max( aresult, bresult ) );
        }));

        return result;
    }

    public Future<Integer> $combineCompletableFuture1( Future<Integer> a, Future<Integer> b) {
        Future<Integer> result = new CompletableFuture<>(); // unresolved

        CompletableFuture.allOf(a, b).then((resArr, err) -> result.complete(Math.max(resArr.get(0).get(), resArr.get(1).get())));

        return result;
    }

    public Future<Integer> $combineCompletableFuture11( Future<Integer> a, Future<Integer> b) {
        List<Future<Integer>> resArr = CompletableFuture.allOf(a, b).await();
        return new CompletableFuture<>(Math.max(resArr.get(0).get(), resArr.get(1).get()));
    }

    public Future<Integer> $combineCompletableFuture2( Future<Integer> a, Future<Integer> b) {
        return new CompletableFuture<>( Math.max( a.await(), b.await() ) );
    }

    public Future<Integer> $raceCompletableFuture0( Future<Integer> a, Future<Integer> b) {
        return new CompletableFuture<>( CompletableFuture.anyOf(a, b).await() );
    }

    //////////////////////////////////////////////////////////////////////////////////////////////
    //
    // chaining, error handling, isolate blocking operations, timed scheduling
    //

    public Future<String> $getUrl(URL url) {
        return exec(() -> new Scanner(url.openStream(), "UTF-8").useDelimiter("\\A").next());
    }

    public void $thenVariations0( URL urlA, URL urlB ) {
        $getUrl(urlA).then(cont -> {
            System.out.println("A receiced");
            self().$getUrl(urlB).then((contentB, err) -> System.out.println("Got B result"));
        })
                .then(() -> System.out.println("hi there"));
    }

    public void $thenVariations1( URL urlA, URL urlB, URL urlC ) {
        $getUrl(urlA).then( contA -> {
            System.out.println("A received");
            return new CompletableFuture<>(self().$getUrl(urlB));
        })
                .then(contentB -> {
                    System.out.println("B received");
                    return self().$getUrl(urlC);
                })
                .then( contentC -> {
                    System.out.println("C received");
                })
                .catchError( err -> {
                    System.out.println("error:"+err);
                });
    }

    public void $thenVariations1UsingAwait( URL urlA, URL urlB, URL urlC ) {
        try {
            self().execute(() -> System.out.println("I am running"));
            String a = $getUrl(urlA).await(5000, TimeUnit.MILLISECONDS);
            String b = $getUrl(urlB).await(5000, TimeUnit.MILLISECONDS);
            String c = $getUrl(urlC).await(5000, TimeUnit.MILLISECONDS);
        } catch (CompletableFuture.TimeoutException | CompletableFuture.ExecutionException e) {
            e.printStackTrace();
        }
    }

    public void $timedExecutionLoop(String output) {
        delayed( 1000, () -> $timedExecutionLoop(output) );
        System.out.println(output);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // callbacks / streaming
    //

    public void $match(String toMatch, Signal<String> cb) {
        stuff.forEach(e -> {
            if (e.contains(toMatch))
                cb.stream(e);
        });
        cb.complete();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // spores
    //

    public void $sporeDemoFullList( Spore<List<String>,Object> spore ) {
        spore.doRemote(stuff);
        spore.finished();
    }

    public void $sporeDemoIterating( Spore<String,String> spore ) {
        for (int i = 0; i < stuff.size() && ! spore.isFinished(); i++) {
            String s = stuff.get(i);
            spore.doRemote(s);
        }
        spore.finished();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // transactions/ordered processing
    //

    ////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // synchronous access, threading trickery
    //

    @Domain.CallerSide
    @Domain.Local
    public int getSyncState() {
        return getNucleus().syncState;
    }
}