package com.dbquality.explain;


/**
 * Factory tạo {@link ExplainParser} phù hợp dựa trên tên database vendor.
 *
 * <p>Sẽ được sử dụng bởi Rule Engine để chọn parser đúng khi phân tích
 * execution plan, dựa trên kết quả từ {@code DatabaseMetaData.getDatabaseProductName()}.</p>
 *
 */
public class ExplainParserFactory {

}
