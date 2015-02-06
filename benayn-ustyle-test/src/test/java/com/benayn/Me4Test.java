package com.benayn;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.benayn.ustyle.Dater;
import com.benayn.ustyle.JsonR;
import com.benayn.ustyle.JsonW;
import com.benayn.ustyle.Objects2;
import com.benayn.ustyle.Objects2.FacadeObject;
import com.benayn.ustyle.Randoms;
import com.benayn.ustyle.Reflecter;
import com.benayn.ustyle.Resolves;
import com.benayn.ustyle.TypeRefer.TypeDescrib;
import com.benayn.ustyle.string.Betner;
import com.benayn.ustyle.string.Finder;
import com.benayn.ustyle.string.Indexer;
import com.benayn.ustyle.string.Replacer;



public class Me4Test extends Me3Test {
    
    @Test
    public void testTmp() {
        
    }
    
    @Test public void testUsage() {

        //set random value to user properties for test
        User user = Randoms.get(User.class);

        Map<Byte, List<Float>> testMapValue = user.getTestMap();
        //set testMap property null
        user.setTestMap(null);

        FacadeObject<User> userWrap = FacadeObject.wrap(user);

        //log as formatted JSON string, see below
        /*
         {
            "birth" : "2015-05-18 02:07:07",
            "testMap" : null,
            "address" : {
               "detail" : "fb232c0cca432c4b11c82f4cf4069405c6f4",
               "lonlat" : {
                  "lon" : 0.46031046103583306,
                  "lat" : 0.23163925851477474
               },
               "code" : -886743908
            },
            "age" : -397182609,
            "name" : "7f9c2734c2965c49fac9788c8dda8a2ace31"
         } 
         */
        userWrap.info();
        assertEquals(JsonW.of(user).asJson(), userWrap.getJson());

        //{"birth":1425988977384,"address":{"detail":"moon",
        //"lonlat":{"lon":0.12,"lat":0.10},"code":30},"age":18,"name":"jack"}
        String json = "{\"birth\":1425988977384,\"address\":{\"detail\":\"moon\",\"lonlat\":"
                + "{\"lon\":0.12,\"lat\":0.10},\"code\":30},\"age\":18,\"name\":\"jack\"}";

        Map<String, Object> jsonMap = JsonR.of(json).deepTierMap();
        //same as jsonMap.get("lon")
        assertEquals(0.12, jsonMap.get("address.lonlat.lon"));  

        //same as jsonMap.get("address.lonlat.lat")
        assertEquals(0.10, jsonMap.get("lat"));                 

        //date
        String dateStr = "2015-03-10 20:02:57";
        long dateMs = (Long) jsonMap.get("birth");
        assertEquals(dateStr, Dater.of(dateMs).asText());

        //populate with map
        User user2 = Reflecter.from(User.class).populate(jsonMap).get();
        assertFalse(Objects2.isEqual(user, user2));             //deeply compare

        //populate with JSON
        userWrap.populate(json);
        assertTrue(Objects2.isEqual(user, user2));
        assertTrue(user.getAddress().getLonlat().getLon() 
               == user2.getAddress().getLonlat().getLon());

        //modify user.address.lonlat.lat
        user.getAddress().getLonlat().setLat(0.2);
        assertFalse(Objects2.isEqual(user, user2));

        //type
        TypeDescrib testMapType = userWrap.getType("testMap");
        assertTrue(testMapType.isPair());
        assertEquals(Byte.class, testMapType.next().rawClazz());
        //nextPairType() same as next(1)
        assertEquals(List.class, testMapType.nextPairType().rawClazz());    
        assertEquals(Float.class, testMapType.next(1).next().rawClazz());
        
        assertEquals(double.class, userWrap.getType("address.lonlat.lon").rawClazz());
        assertEquals(Integer.class, userWrap.getType("address.code").rawClazz());
        
        assertEquals(user.getAddress().getLonlat().getLat(), 
                     userWrap.getValue("address.lonlat.lat"));
        assertEquals(user.getAddress().getDetail(), userWrap.getValue("address.detail"));
        assertEquals(user.getBirth().getTime(), userWrap.<Date>getValue("birth").getTime());

        //resolve type
        Object resolveObj = Resolves.get(userWrap.getField("testMap"), testMapValue);
        assertTrue(resolveObj instanceof Map);
        Map<?, ?> resolveMap = (Map<?, ?>) resolveObj;
        assertTrue(resolveMap.size() > 0);

        for (Object key : resolveMap.keySet()) {
            assertTrue(key instanceof Byte);
            assertTrue(resolveMap.get(key) instanceof List);

            List<?> list = (List<?>) resolveMap.get(key);
            for (Object listVal : list) {
                assertTrue(listVal instanceof Float);
            }
        }

        //some string test
        String str = "helloworld@test.com";

        assertEquals("hello*****@test.com", 
                Replacer.ctx(str).afters(5).befores("@").with('*'));
        assertEquals("*****world*********", 
                Replacer.of(str).after(5).negates().before("@").negates().with('*'));

        assertEquals("test", Finder.of(str).afters("@").befores(".").get());        
        assertEquals("world@test", Indexer.of(str).between(5, -4));
        assertEquals("test", Betner.of(str).between("@", ".").first());
    }
    
}
