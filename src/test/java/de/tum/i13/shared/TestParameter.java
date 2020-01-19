package de.tum.i13.shared;

public class TestParameter {
    public static final String ECS_CLIArgument = "-l generated/ecs1.log -ll ALL -a" +
            " 127.0.0" +
            ".1 -p 5152";
    public static final String ECS_CLIArgument_TWO = "-l generated/ecs2.log -ll " +
            "ALL -a 127.0.0.1 -p 5154 -pp 55554";
    public static final String ECS_CLIArgument_THREE = "-l generated/ecs3.log -ll" +
            " ALL -a 127.0.0.1 -p 5159 -pp 55553";
    public static final String ECS_CLIArgument_FOUR = "-l generated/ecs4.log -ll " +
            "ALL -a 127.0.0.1 -p 5160 -pp 55552";
    public static final String ECS_CLIArgument_FIVE = "-l generated/ecs5.log -ll " +
            "ALL -a 127.0.0.1 -p 5170 -pp 55551";
    public static final String ECS_CLIArgument_SIX = "-l generated/de.tum.i13.ecs.PerformanceTest/ecs6.log -ll " +
            "ALL -a 127.0.0.1 -p 6003 " +
            "-pp 55551";
    
    public static String kv1 = "-l generated/de.tum.i13.ecs.PerformanceTest/kvserver1.log -ll ALL -d " +
            "generated/de.tum.i13.ecs.PerformanceTest/kvstore1/ -a 127.0.0.1 -p 6000 -b 127.0.0.1:6003" + " -c 10 -s LRU";
    public static String kv2 = "-l generated/de.tum.i13.ecs.PerformanceTest/kvserver2.log -ll ALL -d " +
            "generated/de.tum.i13.ecs.PerformanceTest/kvstore2/ -a" +
            " 127.0.0.1" +
            " -p 6001 " +
            "-b 127" +
            ".0.0" +
            ".1:6003" + " -c 10 -s LRU";
    public static String kv3 = "-l generated/de.tum.i13.ecs.PerformanceTest/kvserver3.log -ll ALL -d " +
            "generated/de.tum.i13.ecs.PerformanceTest/kvstore3/ -a" +
            " 127.0.0.1 -p 6002 " +
            "-b 127" +
            ".0.0" +
            ".1:6003" + " -c 10 -s LRU";
    public static String client = "-a 127.0.0.1 -p 5153";
// --Commented out by Inspection START (25. Nov.. 2019 07:29):
//    public static String KV_CLIArgument = "-l kvserver1.log -ll ALL -d " +
//            "data/kvstore1/ -a 192.168.1.2 -p 5153 -b 192.168.1.1:5152";
// --Commented out by Inspection STOP (25. Nov.. 2019 07:29)
}
