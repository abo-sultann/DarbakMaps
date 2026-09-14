package com.abosultan.darbakmaps.location;
import org.junit.Test;import static org.junit.Assert.*;
public class FixQualityTest {
 @Test public void rejectsStaleOrPoorFix(){assertFalse(FixQuality.usable(16000,true,5,25,45));assertFalse(FixQuality.usable(1000,true,100,25,45));assertFalse(FixQuality.usable(0,false,0,25,45));assertFalse(FixQuality.usable(-1,true,5,25,45));assertFalse(FixQuality.usable(0,true,5,Double.NaN,45));assertTrue(FixQuality.usable(1000,true,8,25,45));}
}
