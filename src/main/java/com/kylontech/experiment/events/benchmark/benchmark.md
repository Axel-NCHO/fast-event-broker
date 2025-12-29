# Event router benchmark

## Environment

| Code | CPU                     | RAM  | Java       |
|------|-------------------------|------|------------|
| A    | Intel Core i7 13600H    | 16GB | OpenJDK 25 |
| B    | Intel Core Ultra 7 155H | 32GB | OpenJDK 25 |

## Data

| Nb published events | Nb publishers | Nb subscribers | Nb event dispatches |
|---------------------|---------------|----------------|---------------------|
| 1 000 000           | 4             | 20             | 4 000 000           |

## Results

| Env | Avg    | Epochs | Throughput                   |
|-----|--------|--------|------------------------------|
| A   | 922 ms | 50     | ~4.34 million dispatches/sec |
| B   | 528 ms | 50     | ~7.58 million dispatches/sec |
