from pathlib import Path

p=Path('app/src/main/java/com/abosultan/darbakmaps/map/OfflineMapSearchEngine.java')
s=p.read_text(encoding='utf-8')
s=s.replace('''    private static final int MAX_PAGE_SIZE = 200;\n    private static final int MAX_QUERY_WINDOW = 2000;\n''','''    private static final int MAX_PAGE_SIZE = 200;\n''',1)
s=s.replace('''    private volatile int nextOffset;\n    private String mapIdentity;\n''','''    private volatile int nextOffset;\n    private RankedResult nextPageCursor;\n    private String mapIdentity;\n''',1)
s=s.replace('''        indexedCount = 0; nextOffset = 0; mapIdentity = null;\n''','''        indexedCount = 0; nextOffset = 0; nextPageCursor = null; mapIdentity = null;\n''',1)
old='''        failed = false;\n        queryTruncated = false;\n        nextOffset = request.offset;\n        String wanted = normalize(request.query);\n'''
new='''        failed = false;\n        queryTruncated = false;\n        nextPageCursor = null;\n        nextOffset = request.offset;\n        String wanted = normalize(request.query);\n'''
if old not in s: raise SystemExit('search state block missing')
s=s.replace(old,new,1)
s=s.replace('''        int pageSize = Math.max(5, Math.min(MAX_PAGE_SIZE, request.limit));\n        int window = Math.min(MAX_QUERY_WINDOW, Math.max(pageSize + 1, request.offset + pageSize + 1));\n        BoundedMatches matches = new BoundedMatches(window, request);\n''','''        int pageSize = Math.max(5, Math.min(MAX_PAGE_SIZE, request.limit));\n        // Keyset paging keeps memory O(page size) even on very late pages.\n        BoundedMatches matches = new BoundedMatches(pageSize + 1, request);\n''',1)
old='''        List<RankedResult> sorted = matches.sortedBest();\n        int from = Math.min(request.offset, sorted.size());\n        int to = Math.min(sorted.size(), from + pageSize);\n        List<Result> out = new ArrayList<>();\n        Set<String> emitted = new HashSet<>();\n        for (int i = from; i < to; i++) {\n            Result r = sorted.get(i).result;\n            if (emitted.add(dedupeKey(r))) out.add(r);\n        }\n        queryTruncated = matches.sawBeyondWindow() || sorted.size() > to;\n        nextOffset = queryTruncated ? request.offset + pageSize : request.offset;\n        return out;\n'''
new='''        List<RankedResult> sorted = matches.sortedBest();\n        int to = Math.min(sorted.size(), pageSize);\n        List<Result> out = new ArrayList<>();\n        Set<String> emitted = new HashSet<>();\n        RankedResult lastEmitted = null;\n        for (int i = 0; i < to; i++) {\n            RankedResult ranked = sorted.get(i);\n            Result r = ranked.result;\n            if (emitted.add(dedupeKey(r))) { out.add(r); lastEmitted = ranked; }\n        }\n        queryTruncated = matches.sawBeyondWindow() || sorted.size() > pageSize;\n        if (queryTruncated && lastEmitted != null) {\n            nextPageCursor = lastEmitted;\n            nextOffset = request.offset + pageSize;\n        } else {\n            nextPageCursor = null;\n            nextOffset = request.offset;\n        }\n        return out;\n    }\n\n    public synchronized SearchRequest nextPageRequest(SearchRequest current) {\n        if (current == null || nextPageCursor == null) return current;\n        return new SearchRequest(current.query, current.category, current.centerLatitude, current.centerLongitude,\n                current.radiusMeters, current.sortNearest, current.limit,\n                current.offset + Math.max(5, Math.min(MAX_PAGE_SIZE, current.limit)), nextPageCursor);\n'''
if old not in s: raise SystemExit('page extraction block missing')
s=s.replace(old,new,1)
old='''    public static final class SearchRequest {\n        public final String query, category; public final Double centerLatitude, centerLongitude;\n        public final float radiusMeters; public final boolean sortNearest; public final int limit, offset;\n        public SearchRequest(String q,String c,Double lat,Double lon,float radius,boolean nearest,int limit){this(q,c,lat,lon,radius,nearest,limit,0);}\n        public SearchRequest(String q,String c,Double lat,Double lon,float radius,boolean nearest,int limit,int offset){\n            this.query=q==null?"":q; this.category=c==null?CATEGORY_ALL:c; centerLatitude=lat; centerLongitude=lon;\n            radiusMeters=Math.max(0f,radius); sortNearest=nearest; this.limit=limit; this.offset=Math.max(0,offset);\n        }\n        public SearchRequest nextPage(){return new SearchRequest(query,category,centerLatitude,centerLongitude,radiusMeters,sortNearest,limit,offset+Math.max(5,Math.min(MAX_PAGE_SIZE,limit)));}\n    }\n'''
new='''    public static final class SearchRequest {\n        public final String query, category; public final Double centerLatitude, centerLongitude;\n        public final float radiusMeters; public final boolean sortNearest; public final int limit, offset;\n        private final RankedResult after;\n        public SearchRequest(String q,String c,Double lat,Double lon,float radius,boolean nearest,int limit){this(q,c,lat,lon,radius,nearest,limit,0,null);}\n        public SearchRequest(String q,String c,Double lat,Double lon,float radius,boolean nearest,int limit,int offset){this(q,c,lat,lon,radius,nearest,limit,offset,null);}\n        private SearchRequest(String q,String c,Double lat,Double lon,float radius,boolean nearest,int limit,int offset,RankedResult after){\n            this.query=q==null?"":q; this.category=c==null?CATEGORY_ALL:c; centerLatitude=lat; centerLongitude=lon;\n            radiusMeters=Math.max(0f,radius); sortNearest=nearest; this.limit=limit; this.offset=Math.max(0,offset); this.after=after;\n        }\n    }\n'''
if old not in s: raise SystemExit('SearchRequest block missing')
s=s.replace(old,new,1)
old='''            RankedResult candidate=new RankedResult(result,score); String key=dedupeKey(result); if(heapKeys.contains(key))return;\n            if(heap.size()<capacity){heap.add(candidate);heapKeys.add(key);return;}\n'''
new='''            RankedResult candidate=new RankedResult(result,score);\n            // Keyset cursor: later pages only consider rows strictly after the last row of the previous page.\n            if(request.after!=null && compareRank(candidate,request.after,request)<=0)return;\n            String key=dedupeKey(result); if(heapKeys.contains(key))return;\n            if(heap.size()<capacity){heap.add(candidate);heapKeys.add(key);return;}\n'''
if old not in s: raise SystemExit('offer candidate block missing')
s=s.replace(old,new,1)
s=s.replace('''        return a.result.name.compareTo(b.result.name);\n''','''        int n=a.result.name.compareTo(b.result.name); if(n!=0)return n;\n        return a.result.id.compareTo(b.result.id);\n''',1)
p.write_text(s,encoding='utf-8')

# MainActivity uses engine-created keyset request instead of offset-only paging.
p=Path('app/src/main/java/com/abosultan/darbakmaps/MainActivity.java')
s=p.read_text(encoding='utf-8')
s=s.replace('''        if (queryMore) builder.setPositiveButton("المزيد", (dialog, which) -> executeSearch(request.nextPage(), "بحث"));\n''','''        if (queryMore) builder.setPositiveButton("المزيد", (dialog, which) -> {\n            OfflineMapSearchEngine.SearchRequest next = searchEngine.nextPageRequest(request);\n            if (next != null && next != request) executeSearch(next, "بحث");\n        });\n''',1)
p.write_text(s,encoding='utf-8')

# Replace pagination test with real 3-page search over synthetic saved places.
p=Path('app/src/test/java/com/abosultan/darbakmaps/map/OfflineMapSearchEngineReviewTest.java')
s=p.read_text(encoding='utf-8')
old='''    @Test public void requestSupportsRealPagingOffsets() {\n        OfflineMapSearchEngine.SearchRequest first = new OfflineMapSearchEngine.SearchRequest(\n                "سمان", OfflineMapSearchEngine.CATEGORY_ALL, null, null, 0f, false, 40);\n        assertEquals(0, first.offset);\n        assertEquals(40, first.nextPage().offset);\n        assertEquals(80, first.nextPage().nextPage().offset);\n    }\n'''
new=r'''    @Test public void keysetPagingReachesAllMatchesWithBoundedPages() throws Exception {
        java.lang.reflect.Constructor<com.abosultan.darbakmaps.data.PlaceRepository.Place> ctor =
                com.abosultan.darbakmaps.data.PlaceRepository.Place.class.getDeclaredConstructor(
                        String.class, String.class, double.class, double.class, long.class,
                        String.class, String.class, String.class);
        ctor.setAccessible(true);
        java.util.List<com.abosultan.darbakmaps.data.PlaceRepository.Place> places = new java.util.ArrayList<>();
        for (int i = 0; i < 95; i++) places.add(ctor.newInstance(
                "id-" + i, String.format(java.util.Locale.US, "سمان %03d", i),
                25d + i * 0.00001d, 45d, 1L,
                com.abosultan.darbakmaps.data.PlaceRepository.ICON_QUAIL, "سمان", ""));

        OfflineMapSearchEngine engine = new OfflineMapSearchEngine();
        OfflineMapSearchEngine.SearchRequest request = new OfflineMapSearchEngine.SearchRequest(
                "سمان", OfflineMapSearchEngine.CATEGORY_ALL, null, null, 0f, false, 40);
        java.util.Set<String> ids = new java.util.HashSet<>();
        java.util.List<OfflineMapSearchEngine.Result> page1 = engine.search(request, null, places);
        assertEquals(40, page1.size()); for (OfflineMapSearchEngine.Result r : page1) assertTrue(ids.add(r.id));
        assertTrue(engine.hasMoreResults());
        request = engine.nextPageRequest(request);
        java.util.List<OfflineMapSearchEngine.Result> page2 = engine.search(request, null, places);
        assertEquals(40, page2.size()); for (OfflineMapSearchEngine.Result r : page2) assertTrue(ids.add(r.id));
        assertTrue(engine.hasMoreResults());
        request = engine.nextPageRequest(request);
        java.util.List<OfflineMapSearchEngine.Result> page3 = engine.search(request, null, places);
        assertEquals(15, page3.size()); for (OfflineMapSearchEngine.Result r : page3) assertTrue(ids.add(r.id));
        assertFalse(engine.hasMoreResults());
        assertEquals(95, ids.size());
    }
'''
if old not in s: raise SystemExit('old paging test missing')
p.write_text(s.replace(old,new,1),encoding='utf-8')
