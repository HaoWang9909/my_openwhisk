function main(params) {
    let result = 1;
    for (let i = 1; i <= 1e6; i++) {
        result *= i % 1000;  // 模拟计算
    }
    var stop = new Date().getTime();
    while (new Date().getTime() < stop + 30000) {}
    return { result };
}