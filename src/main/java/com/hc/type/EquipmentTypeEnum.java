package com.hc.type;

/**
 * rabbitmq队列名为 type_desc
 */
public enum EquipmentTypeEnum {
    WRISTSTRAP(1000, "wriststrap"),
    ROBOT_ANDROID(2000, "robot_android");

    private int type;
    private String desc;

    EquipmentTypeEnum(int type, String desc) {
        this.type = type;
        this.desc = desc;
    }

    public int getType() {
        return type;
    }

    public String getDesc() {
        return desc;
    }

    //TODO 缓存池
    public String getQueueName() {
        return type + "_" + desc;
    }

    public static EquipmentTypeEnum getEnumByCode(int code) {
        for (EquipmentTypeEnum equipmentTypeEnum : EquipmentTypeEnum.values()) {
            if (code == equipmentTypeEnum.type) {
                return equipmentTypeEnum;
            }
        }
        throw new RuntimeException("设备类型枚举不存在！");
    }

}
