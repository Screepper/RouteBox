interface PageQuery {
  currentPage: number;
  pageSize: number;
}

interface PageVO<T> {
  total: number;
  records: T[];
}

export type { PageQuery, PageVO };
