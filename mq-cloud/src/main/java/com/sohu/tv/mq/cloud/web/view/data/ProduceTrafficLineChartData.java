package com.sohu.tv.mq.cloud.web.view.data;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.sohu.tv.mq.cloud.bo.TopicTraffic;
import com.sohu.tv.mq.cloud.bo.Traffic;
import com.sohu.tv.mq.cloud.service.TopicTrafficService;
import com.sohu.tv.mq.cloud.util.DateUtil;
import com.sohu.tv.mq.cloud.util.Result;
import com.sohu.tv.mq.cloud.web.view.SearchHeader;
import com.sohu.tv.mq.cloud.web.view.SearchHeader.DateSearchField;
import com.sohu.tv.mq.cloud.web.view.SearchHeader.HiddenSearchField;
import com.sohu.tv.mq.cloud.web.view.SearchHeader.SearchField;
import com.sohu.tv.mq.cloud.web.view.chart.LineChart;
import com.sohu.tv.mq.cloud.web.view.chart.LineChart.XAxis;
import com.sohu.tv.mq.cloud.web.view.chart.LineChart.YAxis;
import com.sohu.tv.mq.cloud.web.view.chart.LineChart.YAxisGroup;
import com.sohu.tv.mq.cloud.web.view.chart.LineChartData;

/**
 * topic生产流量数据
 * 
 * @Description:
 * @author yongfeigao
 * @date 2018年6月29日
 */
@Component
public class ProduceTrafficLineChartData implements LineChartData {

    // 搜索区域
    private SearchHeader searchHeader;

    public static final String DATE_FIELD = "date";
    public static final String TID_FIELD = "tid";
    public static final String DATE_FIELD_TITLE = "日期";

    // x轴数据
    private List<String> xDataList;

    // x轴格式化后的数据
    private List<String> xDataFormatList;

    @Autowired
    private TopicTrafficService topicTrafficService;

    public ProduceTrafficLineChartData() {
        initSearchHeader();
    }

    /**
     * 初始化搜索数据
     */
    public void initSearchHeader() {
        searchHeader = new SearchHeader();
        List<SearchField> searchFieldList = new ArrayList<SearchHeader.SearchField>();

        // time
        DateSearchField dateSearchField = new DateSearchField();
        dateSearchField.setKey(DATE_FIELD);
        dateSearchField.setTitle(DATE_FIELD_TITLE);
        searchFieldList.add(dateSearchField);

        // hidden
        HiddenSearchField hiddenSearchField = new HiddenSearchField();
        hiddenSearchField.setKey(TID_FIELD);
        searchFieldList.add(hiddenSearchField);

        searchHeader.setSearchFieldList(searchFieldList);

        // 初始化x轴数据，因为x轴数据是固定的
        xDataFormatList = new ArrayList<String>();
        xDataList = new ArrayList<String>();
        for (int i = 0; i < 23; ++i) {
            for (int j = 0; j < 60; ++j) {
                String hour = i < 10 ? "0" + i : "" + i;
                String ninutes = j < 10 ? "0" + j : "" + j;
                xDataList.add(hour + ninutes);
                xDataFormatList.add(hour + ":" + ninutes);
            }
        }
    }

    @Override
    public String getPath() {
        return "produce";
    }

    @Override
    public String getPageTitle() {
        return "topic生产流量";
    }

    @Override
    public List<LineChart> getLineChartData(Map<String, Object> searchMap) {
        // 多个曲线图列表 - 单x多series双y轴
        List<LineChart> lineChartList = new ArrayList<LineChart>();
        
        // 解析参数
        Date date = getDate(searchMap, DATE_FIELD);
        String dateStr = DateUtil.formatYMD(date);
        Long tid = getLongValue(searchMap, TID_FIELD);
        
        if (tid == null || tid <= 0) {
            return lineChartList;
        }
        //获取topic流量
        Result<List<TopicTraffic>> result = topicTrafficService.query(tid, dateStr);
        if (!result.isOK()) {
            return lineChartList;
        }

        Date dayBefore = new Date(date.getTime() - 24*60*60*1000);
        //获取前一天topic流量
        Result<List<TopicTraffic>> resultDayBefore = topicTrafficService.query(tid, DateUtil.formatYMD(dayBefore));
        
        // 构造曲线图对象
        LineChart lineChart = new LineChart();
        lineChart.setChartId("topic");
        lineChart.setOneline(true);
        lineChart.setTickInterval(6);
        XAxis xAxis = new XAxis();
        xAxis.setxList(xDataFormatList);
        lineChart.setxAxis(xAxis);
        
        // 将list转为map方便数据查找
        Map<String, Traffic> trafficMap = list2Map(result.getResult());
        Map<String, Traffic> trafficMapDayBefore = list2Map(
                resultDayBefore.isOK() ? resultDayBefore.getResult() : null);
        // 填充y轴数据
        List<Number> countList = new ArrayList<Number>();
        List<Number> sizeList = new ArrayList<Number>();
        List<Number> countListDayBefore = new ArrayList<Number>();
        List<Number> sizeListDayBefore = new ArrayList<Number>();
        long maxCount = 0;
        long maxSize = 0;
        long maxCountDayBefore = 0;
        long maxSizeDayBefore = 0;
        long totalCount = 0;
        long totalSize = 0;
        long totalCountDayBefore = 0;
        long totalSizeDayBefore = 0;
        for (String time : xDataList) {
            long count = setCountData(trafficMap.get(time), countList);
            totalCount += count;
            if (maxCount < count) {
                maxCount = count;
            }
            long size = setSizeData(trafficMap.get(time), sizeList);
            totalSize += size;
            if(maxSize < size) {
                maxSize = size;
            }
            long countDayBefore = setCountData(trafficMapDayBefore.get(time), countListDayBefore);
            totalCountDayBefore += countDayBefore;
            if(maxCountDayBefore < countDayBefore) {
                maxCountDayBefore = countDayBefore;
            }
            long sizeDayBefore = setSizeData(trafficMapDayBefore.get(time), sizeListDayBefore);
            totalSizeDayBefore += sizeDayBefore;
            if(maxSizeDayBefore < sizeDayBefore) {
                maxSizeDayBefore = sizeDayBefore;
            }
        }

        String curDate = DateUtil.formatYMD(date);
        String curDateBefore = DateUtil.formatYMD(dayBefore);
        // 设置消息量y轴
        List<YAxis> countYAxisList = new ArrayList<YAxis>();
        YAxis countYAxis = new YAxis();
        countYAxis.setName(curDate + "-消息量");
        countYAxis.setData(countList);
        countYAxisList.add(countYAxis);
        YAxis countYAxisDayBefore = new YAxis();
        countYAxisDayBefore.setVisible(false);
        countYAxisDayBefore.setName(curDateBefore + "-消息量");
        countYAxisDayBefore.setData(countListDayBefore);
        countYAxisList.add(countYAxisDayBefore);

        // 生成y轴数据组对象
        YAxisGroup countYAxisGroup = new YAxisGroup();
        countYAxisGroup.setGroupName("消息量");
        countYAxisGroup.setyAxisList(countYAxisList);

        // 设置消息大小y轴
        List<YAxis> sizeYAxisList = new ArrayList<YAxis>();
        YAxis sizeYAxis = new YAxis();
        sizeYAxis.setVisible(false);
        sizeYAxis.setName(curDate + "-消息大小");
        sizeYAxis.setData(sizeList);
        sizeYAxisList.add(sizeYAxis);
        YAxis sizeYAxisDayBefore = new YAxis();
        sizeYAxisDayBefore.setVisible(false);
        sizeYAxisDayBefore.setName(curDateBefore + "-消息大小");
        sizeYAxisDayBefore.setData(sizeListDayBefore);
        sizeYAxisList.add(sizeYAxisDayBefore);

        // 生成y轴数据组对象
        YAxisGroup sizeYAxisGroup = new YAxisGroup();
        sizeYAxisGroup.setGroupName("消息大小");
        sizeYAxisGroup.setOpposite(true);
        sizeYAxisGroup.setTraffic(true);
        sizeYAxisGroup.setyAxisList(sizeYAxisList);

        // 设置双y轴
        List<YAxisGroup> yAxisGroupList = new ArrayList<YAxisGroup>();
        yAxisGroupList.add(countYAxisGroup);
        yAxisGroupList.add(sizeYAxisGroup);
        lineChart.setyAxisGroupList(yAxisGroupList);

        lineChart.setSubTitle("日期: "+curDate+", 消息量峰值: " + formatCount(maxCount) + "/分, 消息大小峰值: " + formatSize(maxSize) + "/分, "
                + "消息总量:" + formatCount(totalCount) + ", 消息总大小: " + formatSize(totalSize) + "<br>"
                + "日期: "+curDateBefore+", 消息量峰值: " + formatCount(maxCountDayBefore) + "/分, 消息大小峰值: " + formatSize(maxSizeDayBefore) + "/分, "
                + "消息总量:" + formatCount(totalCountDayBefore) + ", 消息总大小: " + formatSize(totalSizeDayBefore) + "<br>");
        
        lineChart.setHeight(450);
        lineChart.setSubTitle("<table cellspacing='0' cellpadding='0' style='background-color: #f5f5f5'><thead><tr>"
                + "<td>日期</td><td>消息量峰值</td><td>消息总量</td><td>消息大小峰值</td><td>消息总大小</td></tr></thead>"
                + "<tbody><tr><td>"+curDate+"</td>"
                + "<td>"+formatCount(maxCount)+"/分</td>"
                + "<td>"+formatCount(totalCount)+"</td>"
                + "<td>"+formatSize(maxSize)+"/分</td>"
                + "<td>"+formatSize(totalSize)+"</td>"
                + "</tr><tr><td>"+curDateBefore+"</td>"
                + "<td>"+formatCount(maxCountDayBefore)+"/分</td>"
                + "<td>"+formatCount(totalCountDayBefore)+"</td>"
                + "<td>"+formatSize(maxSizeDayBefore)+"/分</td>"
                + "<td>"+formatSize(totalSizeDayBefore)+"</td>"
                + "</tr></tbody></table>");

        lineChartList.add(lineChart);
        
        return lineChartList;
    }
    
    /**
     * 格式化消息数量
     * @param maxCount
     * @return
     */
    private String formatCount(long maxCount) {
        String maxCountShow = "";
        if(maxCount > 100000000) {
            maxCountShow = String.format("%.2f", maxCount / 100000000F) + "亿("+maxCount+")";
        } else if(maxCount > 10000) {
            maxCountShow = String.format("%.2f", maxCount / 10000F) + "万("+maxCount+")";
        } else {
            maxCountShow = String.valueOf(maxCount);
        }
        return maxCountShow;
    }
    
    /**
     * 格式化消息大小
     * @param maxSize
     * @return
     */
    private String formatSize(long maxSize) {
        String maxSizeShow = "";
        if(maxSize > 1073741824) {
            maxSizeShow = String.format("%.2f", maxSize / 1073741824F) + "G";
        } else if(maxSize > 1048576) {
            maxSizeShow = String.format("%.2f", maxSize / 1048576F) + "M";
        } else if (maxSize > 1024) {
            maxSizeShow = String.format("%.2f", maxSize / 1024F) + "K";
        } else {
            maxSizeShow = maxSize + "B";
        }
        return maxSizeShow;
    }

    private long setSizeData(Traffic traffic, List<Number> sizeList) {
        if (traffic == null) {
            sizeList.add(0);
            return 0;
        } else {
            sizeList.add(traffic.getSize());
            return traffic.getSize();
        }
    }

    private long setCountData(Traffic traffic, List<Number> countList) {
        if (traffic == null) {
            countList.add(0);
            return 0;
        } else {
            countList.add(traffic.getCount());
            return traffic.getCount();
        }
    }

    private Map<String, Traffic> list2Map(List<? extends Traffic> list) {
        Map<String, Traffic> map = new TreeMap<String, Traffic>();
        if (list == null) {
            return map;
        }
        for (Traffic traffic : list) {
            map.put(traffic.getCreateTime(), traffic);
        }
        return map;
    }
    
    /**
     * 获取长整型数据
     * 
     * @param searchMap
     * @param key
     * @return
     */
    protected Long getLongValue(Map<String, Object> searchMap, String key) {
        if (searchMap == null) {
            return null;
        }
        Object obj = searchMap.get(key);
        if (obj == null) {
            return null;
        }
        return NumberUtils.toLong(obj.toString());
    }

    /**
     * 获取日期数据
     * 
     * @param searchMap
     * @param key
     * @return
     */
    protected Date getDate(Map<String, Object> searchMap, String key) {
        if (searchMap == null) {
            return new Date();
        }
        Object obj = searchMap.get(key);
        if (obj == null) {
            return new Date();
        }
        String date = obj.toString();
        if (!StringUtils.isEmpty(date)) {
            return DateUtil.parseYMD(date);
        }
        return new Date();
    }

    @Override
    public SearchHeader getSearchHeader() {
        return searchHeader;
    }

}
