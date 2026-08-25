from time import time

WINDOW_SIZE = 60
PERIOD_SECONDS = 60 * 60 * 24 * 7  # period of 7 days

if __name__ == "__main__":
    # Import your evaluator here
    from METED import METED

    # 1. Create evaluator instance
    evaluator = METED()
    print("Starting batch evaluation for 8 AS nodes...")
    results = []

    # 2. Evaluate each node
    start_time = time()
    for node_id in range(1, 9):
        train_path = f"data/train/{node_id}.csv"
        eval_path = f"data/eval/{node_id}.csv"


        print(f"\nEvaluating Node {node_id}...")
        # Implement Your eval function (use the train_path and eval_path to train and evaluate)
        precision, recall, f1 = evaluator.eval(train_path, eval_path)
        results.append((node_id, precision, recall, f1))
        end_time = time()

        print(f"Elapsed Time: {int(end_time - start_time)}s -> Precision: {precision:.8f} | Recall: {recall:.8f} | F1: {f1:.8f}")

    # 3. Final results
    print("\n" + "=" * 55)
    print(f"{'Node':<6} | {'Precision':<10} | {'Recall':<10} | {'F1-Score':<10}")
    print("-" * 55)
    for nid, p, r, f in results:
        print(f"{nid:<6} | {p:<10.4f} | {r:<10.4f} | {f:<10.4f}")
    print(
        f"\nMacro Avg -> Precision: {sum(r[1] for r in results) / 8:.4f} | "
        f"Recall: {sum(r[2] for r in results) / 8:.4f} | F1: {sum(r[3] for r in results) / 8:.4f}"
    )
