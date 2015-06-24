/**
 * org.darwin.genericDao.dao.impl.GenericStatDao.java created by Tianxin(tianjige@163.com) on
 * 2015年6月3日 下午2:05:00
 */
package org.darwin.genericDao.dao.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.darwin.common.utils.GenericDaoUtils;
import org.darwin.common.utils.Utils;
import org.darwin.genericDao.annotations.enums.Type;
import org.darwin.genericDao.dao.BaseStatDao;
import org.darwin.genericDao.mapper.BasicMappers;
import org.darwin.genericDao.mapper.ColumnMapper;
import org.darwin.genericDao.mapper.EntityMapper;
import org.darwin.genericDao.operate.Groups;
import org.darwin.genericDao.operate.Matches;
import org.darwin.genericDao.operate.Orders;
import org.darwin.genericDao.param.SQLParams;
import org.darwin.genericDao.query.QueryStat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 统计表的默认DAO实现
 * created by Tianxin on 2015年6月3日 下午2:05:00
 */
public class GenericStatDao<ENTITY> extends AbstractGenericDao<ENTITY> implements BaseStatDao<ENTITY>{

  /**
   * static slf4j logger instance
   */
  protected final static Logger LOG = LoggerFactory.getLogger(GenericStatDao.class);

  /**
   * 无参构造函数
   */
  public GenericStatDao() {
    super();
    this.analysisColumns(columnMappers);
  }

  /**
   * 分析statObject中的所有列类型
   * 
   * @param columnMappers created by Tianxin on 2015年6月3日 下午3:56:37
   */
  private void analysisColumns(Map<String, ColumnMapper> columnMappers) {
    Collection<ColumnMapper> mappers = columnMappers.values();
    for (ColumnMapper mapper : mappers) {

      // 如果咩有统计类型的设置，则直接跳过
      allColumns.add(mapper.getSQLColumn());
      if (mapper.getType() == null) {
        continue;
      }

      // 根据统计类型放到不同的地方，这里使用switch，编译后会出现内部类，导致一些莫名的错误，暂时使用ifelse
      Type type = mapper.getType().value();
      if (Type.KEY == type) {
        keyColumns.add(mapper.getSQLColumn());
      } else if (Type.SUM == type) {
        sumColumns.add(mapper.getSQLColumn());
      } else if (Type.AVG == type) {
        avgColumns.add(mapper.getSQLColumn());
      } else if (Type.EXTEND == type) {
        extendColumns.add(mapper.getSQLColumn());
      } else if (Type.DATE == type) {
        dateColumn = mapper.getSQLColumn();
        keyColumns.add(dateColumn);
      }
    }
  }

  private String dateColumn = null;
  private List<String> sumColumns = new ArrayList<String>();
  private List<String> avgColumns = new ArrayList<String>();
  private List<String> keyColumns = new ArrayList<String>();
  private List<String> allColumns = new ArrayList<String>();
  private List<String> extendColumns = new ArrayList<String>();

  /**
   * 获取数据，如果需要按时间聚合，则按其他字段groupBy，把不同时间的汇总到一起
   * 
   * @param aggregationByDate
   * @return created by Tianxin on 2015年6月3日 下午8:15:49
   */
  public List<ENTITY> statAll(boolean aggregationByDate) {
    Groups groups = aggregationByDate ? generateAggergationByDateGroups() : Groups.init();
    return statByMgo(null, groups, null);
  }

  /**
   * 根据匹配条件，分组规则，排序规则进行统计
   * 
   * @param matches
   * @param groups
   * @param orders
   * @return created by Tianxin on 2015年6月4日 下午8:23:27
   */
  public ENTITY statByMatches(Matches matches) {
    List<ENTITY> entities = statByMgo(matches, null, null);

    if (Utils.isEmpty(entities)) {
      return null;
    }

    return entities.get(0);
  }
  
  /**
   * 根据匹配条件，分组规则，排序规则进行统计
   * 
   * @param matches
   * @param groups
   * @param orders
   * @return created by Tianxin on 2015年6月4日 下午8:23:27
   */
  public <R> List<R> statOneColumnByMgo(Matches matches, Groups groups, Orders orders, String column, Class<R> rClass) {
    return statPageOneColumnByMgo(matches, groups, orders, column, rClass, 0, 0);
  }
  
  /**
   * 根据匹配条件，分组规则，排序规则进行统计
   * 
   * @param matches
   * @param groups
   * @param orders
   * @return created by Tianxin on 2015年6月4日 下午8:23:27
   */
  public <R> List<R> statPageOneColumnByMgo(Matches matches, Groups groups, Orders orders, String column, Class<R> rClass, int offset, int rows) {
    QueryStat query = new QueryStat(new ArrayList<String>(0), new ArrayList<String>(0), Arrays.asList(column) , new ArrayList<String>(0), matches, orders, groups, configKeeper.table(), offset, rows);
    String sql = query.getSQL();
    Object[] params = query.getParams();
    LOG.info(Utils.toLogSQL(sql, params));
    return jdbcTemplate.query(sql, params, BasicMappers.getMapper(rClass));
  }

  /**
   * 根据匹配条件，分组规则，排序规则进行统计
   * 
   * @param matches
   * @param groups
   * @param orders
   * @return created by Tianxin on 2015年6月4日 下午8:23:27
   */
  public List<ENTITY> statByMgo(Matches matches, Groups groups, Orders orders) {
    QueryStat query = new QueryStat(sumColumns, avgColumns, extendColumns, keyColumns, matches, orders, groups, configKeeper.table());
    return statByQuery(query);
  }

  /**
   * 根据匹配条件，分组规则，排序规则进行统计
   * 
   * @param matches
   * @param groups
   * @param orders
   * @return created by Tianxin on 2015年6月4日 下午8:23:27
   */
  public List<ENTITY> statPageByMgo(Matches matches, Groups groups, Orders orders, int offset, int rows) {
    QueryStat query = new QueryStat(sumColumns, avgColumns, extendColumns, keyColumns, matches, orders, groups, configKeeper.table(), offset, rows);
    return statByQuery(query);
  }

  /**
   * 根据匹配条件，分组规则，计算数据总数
   * 
   * @param matches
   * @param groups
   * @return created by Tianxin on 2015年6月4日 下午8:23:27
   */
  public int statCountByMg(Matches matches, Groups groups) {
    List<String> columns = groups.getGroupByColumns();
    return countDistinct(matches, columns.toArray(new String[columns.size()]));
  }

  /**
   * 按Query进行查询
   * 
   * @param query
   * @return created by Tianxin on 2015年6月3日 下午4:06:19
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  protected List<ENTITY> statByQuery(QueryStat query) {
    String sql = query.getSQL();
    Object[] params = query.getParams();
    List<String> columns = GenericDaoUtils.getColumnsFromSQL(sql);
    LOG.info(Utils.toLogSQL(sql, params));
    return jdbcTemplate.query(sql, params, new EntityMapper(columns, columnMappers, entityClass));
  }

  /**
   * 查询某个时间范围的数据
   * 
   * @param startDate
   * @param endDate
   * @param aggregationByDate
   * @return created by Tianxin on 2015年6月3日 下午8:16:37
   */
  public List<ENTITY> statByRange(int startDate, int endDate, boolean aggregationByDate) {
    Matches matches = Matches.one(dateColumn, SQLParams.between(startDate, endDate));
    Groups groups = aggregationByDate ? generateAggergationByDateGroups() : Groups.init();
    return statByMgo(matches, groups, null);
  }

  /**
   * 当按时间聚合时，groupBy的字段为除去date字段的其他key字段
   * 
   * @return created by Tianxin on 2015年6月3日 下午8:16:55
   */
  private Groups generateAggergationByDateGroups() {
    Groups groups = Groups.init();
    for (String column : keyColumns) {
      if (!column.equals(dateColumn)) {
        groups.groupBy(column);
      }
    }
    return groups;
  }
}
