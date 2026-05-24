package com.zjkl.ai.image.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 图片元素 DTO
 * 用于从记忆内容中提取图片生成所需的结构化元素
 * 输出格式受 JSON Schema 约束
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ImageElements {
    
    /**
     * 人物穿着类别
     * 可选值：校服、休闲装、运动装、家居服、正装、其他
     */
    private String clothing;
    
    /**
     * 具体穿着描述（可选）
     * 如："白色衬衫 + 蓝色百褶裙"、"灰色连帽卫衣"
     */
    private String clothingDetail;
    
    /**
     * 场景环境类别
     * 可选值：教室、卧室、客厅、公园、街道、咖啡厅、图书馆、海边、山林、其他
     */
    private String scene;
    
    /**
     * 场景详细描述（可选）
     * 如："洒满阳光的窗边书桌"、"开满樱花的公园长椅"
     */
    private String sceneDetail;
    
    /**
     * 时间
     * 可选值：清晨、上午、中午、下午、黄昏、夜晚、深夜
     */
    private String timeOfDay;
    
    /**
     * 整体氛围
     * 可选值：温馨、欢快、安静、浪漫、兴奋、沉思、忧郁、其他
     */
    private String atmosphere;
    
    /**
     * 关键道具列表（最多 3 个）
     * 如：["书本", "咖啡", "相机"]
     */
    private List<String> keyProps;
    
    /**
     * 人物情绪
     * 可选值：开心、平静、期待、思念、兴奋、疲惫、其他
     */
    private String emotion;
    
    /**
     * 选择这个场景的理由（50 字内）
     */
    private String reason;

}
