-- WRK Lua script for REST Gateway load testing
-- Simulates order submission load with proper authentication

-- Request body template
wrk.method = "POST"
wrk.body = '{"symbol":"AAPL","side":"BUY","orderType":"LIMIT","quantity":100,"price":15000,"account":999}'
wrk.headers["Content-Type"] = "application/json"

-- Optional: Add authorization header if needed
-- wrk.headers["Authorization"] = "Bearer YOUR_TOKEN_HERE"

-- Track request statistics
local counter = 0
local errors = 0

-- Called for each request
function request()
    counter = counter + 1
    return wrk.format(nil, nil, nil, wrk.body)
end

-- Called on response
function response(status, headers, body)
    if status ~= 200 and status ~= 201 then
        errors = errors + 1
    end
end

-- Print summary
function done(summary, latency, requests)
    io.write("------------------------------\n")
    io.write("WRK Load Test Summary\n")
    io.write("------------------------------\n")
    io.write(string.format("Requests: %d\n", counter))
    io.write(string.format("Errors: %d\n", errors))
    io.write(string.format("Duration: %.2fs\n", summary.duration / 1000000))
    io.write(string.format("Requests/sec: %.2f\n", counter / (summary.duration / 1000000)))
    io.write(string.format("Latency min: %.2fms\n", latency.min / 1000))
    io.write(string.format("Latency mean: %.2fms\n", latency.mean / 1000))
    io.write(string.format("Latency max: %.2fms\n", latency.max / 1000))
    io.write(string.format("Latency stdev: %.2fms\n", latency.stdev / 1000))
    io.write("------------------------------\n")
end
