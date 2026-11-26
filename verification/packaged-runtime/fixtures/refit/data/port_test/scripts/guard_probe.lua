-- Test pack only. Drive failures through the real LuaJ host, including a nested, immediately due cycle.
return {
    tick_reload = function(api)
        local probe = api:getCachedScriptData()
        if probe == "bad ordinal" then return 99, -1 end
        if probe == "nested error" then
            api:safeAsyncTask(function() error("port_test: expected nested callback error") end, 0, 1, 1)
        end
        return 0, -1
    end
}
