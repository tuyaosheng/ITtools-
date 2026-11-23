// 自动生成，不要手改：node scripts/mpblocks-spec.mjs（数据来自本机 Mind+ 2.0.7 的掌控板、Wi-Fi、MQTT 积木）
window.MP_SPEC = {
 "blocks": [
  {
   "type": "control_wait",
   "cat": "control",
   "colour": "#FFAB19",
   "icon": false,
   "shape": "stack",
   "lines": [
    {
     "text": "等待 %1 秒",
     "args": [
      {
       "type": "input_value",
       "name": "DURATION",
       "shadow": {
        "type": "math_positive_number",
        "fields": {
         "NUM": "1"
        }
       }
      }
     ]
    }
   ]
  },
  {
   "type": "control_repeat",
   "cat": "control",
   "colour": "#FFAB19",
   "icon": false,
   "shape": "c",
   "lines": [
    {
     "text": "重复执行 %1 次",
     "args": [
      {
       "type": "input_value",
       "name": "TIMES",
       "shadow": {
        "type": "math_whole_number",
        "fields": {
         "NUM": "10"
        }
       }
      }
     ]
    },
    {
     "text": "%1",
     "args": [
      {
       "type": "input_statement",
       "name": "SUBSTACK"
      }
     ]
    },
    {
     "text": "*",
     "args": []
    }
   ]
  },
  {
   "type": "control_forever",
   "cat": "control",
   "colour": "#FFAB19",
   "icon": false,
   "shape": "c_end",
   "lines": [
    {
     "text": "循环执行",
     "args": []
    },
    {
     "text": "%1",
     "args": [
      {
       "type": "input_statement",
       "name": "SUBSTACK"
      }
     ]
    },
    {
     "text": "*",
     "args": []
    }
   ]
  },
  {
   "type": "control_if",
   "cat": "control",
   "colour": "#FFAB19",
   "icon": false,
   "shape": "c",
   "lines": [
    {
     "text": "如果 %1 那么执行",
     "args": [
      {
       "type": "input_value",
       "name": "CONDITION"
      }
     ]
    },
    {
     "text": "%1",
     "args": [
      {
       "type": "input_statement",
       "name": "SUBSTACK"
      }
     ]
    }
   ]
  },
  {
   "type": "control_if_else",
   "cat": "control",
   "colour": "#FFAB19",
   "icon": false,
   "shape": "c",
   "lines": [
    {
     "text": "如果 %1 那么执行",
     "args": [
      {
       "type": "input_value",
       "name": "CONDITION"
      }
     ]
    },
    {
     "text": "%1",
     "args": [
      {
       "type": "input_statement",
       "name": "SUBSTACK"
      }
     ]
    },
    {
     "text": "否则",
     "args": []
    },
    {
     "text": "%1",
     "args": [
      {
       "type": "input_statement",
       "name": "SUBSTACK2"
      }
     ]
    }
   ]
  },
  {
   "type": "control_if_else_special",
   "cat": "control",
   "colour": "#FFAB19",
   "icon": true,
   "shape": "c",
   "lines": [
    {
     "text": "如果 %1 那么",
     "args": [
      {
       "type": "input_value",
       "name": "IF0"
      }
     ]
    },
    {
     "text": "%1",
     "args": [
      {
       "type": "input_statement",
       "name": "DO0"
      }
     ]
    }
   ]
  },
  {
   "type": "control_wait_until",
   "cat": "control",
   "colour": "#FFAB19",
   "icon": false,
   "shape": "stack",
   "lines": [
    {
     "text": "等待直到 %1",
     "args": [
      {
       "type": "input_value",
       "name": "CONDITION"
      }
     ]
    }
   ]
  },
  {
   "type": "control_repeat_until",
   "cat": "control",
   "colour": "#FFAB19",
   "icon": false,
   "shape": "c",
   "lines": [
    {
     "text": "重复执行直到 %1",
     "args": [
      {
       "type": "input_value",
       "name": "CONDITION"
      }
     ]
    },
    {
     "text": "%1",
     "args": [
      {
       "type": "input_statement",
       "name": "SUBSTACK"
      }
     ]
    },
    {
     "text": "*",
     "args": []
    }
   ]
  },
  {
   "type": "operator_add",
   "cat": "operators",
   "colour": "#59C059",
   "icon": false,
   "shape": "reporter",
   "lines": [
    {
     "text": "%1 + %2",
     "args": [
      {
       "type": "input_value",
       "name": "NUM1",
       "shadow": {
        "type": "math_number",
        "fields": {
         "NUM": ""
        }
       }
      },
      {
       "type": "input_value",
       "name": "NUM2",
       "shadow": {
        "type": "math_number",
        "fields": {
         "NUM": ""
        }
       }
      }
     ]
    }
   ]
  },
  {
   "type": "operator_subtract",
   "cat": "operators",
   "colour": "#59C059",
   "icon": false,
   "shape": "reporter",
   "lines": [
    {
     "text": "%1 - %2",
     "args": [
      {
       "type": "input_value",
       "name": "NUM1",
       "shadow": {
        "type": "math_number",
        "fields": {
         "NUM": ""
        }
       }
      },
      {
       "type": "input_value",
       "name": "NUM2",
       "shadow": {
        "type": "math_number",
        "fields": {
         "NUM": ""
        }
       }
      }
     ]
    }
   ]
  },
  {
   "type": "operator_multiply",
   "cat": "operators",
   "colour": "#59C059",
   "icon": false,
   "shape": "reporter",
   "lines": [
    {
     "text": "%1 * %2",
     "args": [
      {
       "type": "input_value",
       "name": "NUM1",
       "shadow": {
        "type": "math_number",
        "fields": {
         "NUM": ""
        }
       }
      },
      {
       "type": "input_value",
       "name": "NUM2",
       "shadow": {
        "type": "math_number",
        "fields": {
         "NUM": ""
        }
       }
      }
     ]
    }
   ]
  },
  {
   "type": "operator_divide",
   "cat": "operators",
   "colour": "#59C059",
   "icon": false,
   "shape": "reporter",
   "lines": [
    {
     "text": "%1 / %2",
     "args": [
      {
       "type": "input_value",
       "name": "NUM1",
       "shadow": {
        "type": "math_number",
        "fields": {
         "NUM": ""
        }
       }
      },
      {
       "type": "input_value",
       "name": "NUM2",
       "shadow": {
        "type": "math_number",
        "fields": {
         "NUM": ""
        }
       }
      }
     ]
    }
   ]
  },
  {
   "type": "operator_random",
   "cat": "operators",
   "colour": "#59C059",
   "icon": false,
   "shape": "reporter",
   "lines": [
    {
     "text": "在 %1 和 %2 之间取随机数",
     "args": [
      {
       "type": "input_value",
       "name": "FROM",
       "shadow": {
        "type": "math_number",
        "fields": {
         "NUM": "1"
        }
       }
      },
      {
       "type": "input_value",
       "name": "TO",
       "shadow": {
        "type": "math_number",
        "fields": {
         "NUM": "10"
        }
       }
      }
     ]
    }
   ]
  },
  {
   "type": "operator_gt",
   "cat": "operators",
   "colour": "#59C059",
   "icon": false,
   "shape": "boolean",
   "lines": [
    {
     "text": "%1 > %2",
     "args": [
      {
       "type": "input_value",
       "name": "OPERAND1",
       "shadow": {
        "type": "text",
        "fields": {
         "TEXT": ""
        },
        "quote": false
       }
      },
      {
       "type": "input_value",
       "name": "OPERAND2",
       "shadow": {
        "type": "text",
        "fields": {
         "TEXT": "50"
        },
        "quote": false
       }
      }
     ]
    }
   ]
  },
  {
   "type": "operator_gt_equals",
   "cat": "operators",
   "colour": "#59C059",
   "icon": false,
   "shape": "boolean",
   "lines": [
    {
     "text": "%1 >= %2",
     "args": [
      {
       "type": "input_value",
       "name": "OPERAND1",
       "shadow": {
        "type": "text",
        "fields": {
         "TEXT": ""
        },
        "quote": false
       }
      },
      {
       "type": "input_value",
       "name": "OPERAND2",
       "shadow": {
        "type": "text",
        "fields": {
         "TEXT": ""
        },
        "quote": false
       }
      }
     ]
    }
   ]
  },
  {
   "type": "operator_lt",
   "cat": "operators",
   "colour": "#59C059",
   "icon": false,
   "shape": "boolean",
   "lines": [
    {
     "text": "%1 < %2",
     "args": [
      {
       "type": "input_value",
       "name": "OPERAND1",
       "shadow": {
        "type": "text",
        "fields": {
         "TEXT": ""
        },
        "quote": false
       }
      },
      {
       "type": "input_value",
       "name": "OPERAND2",
       "shadow": {
        "type": "text",
        "fields": {
         "TEXT": "50"
        },
        "quote": false
       }
      }
     ]
    }
   ]
  },
  {
   "type": "operator_lt_equals",
   "cat": "operators",
   "colour": "#59C059",
   "icon": false,
   "shape": "boolean",
   "lines": [
    {
     "text": "%1 <= %2",
     "args": [
      {
       "type": "input_value",
       "name": "OPERAND1",
       "shadow": {
        "type": "text",
        "fields": {
         "TEXT": ""
        },
        "quote": false
       }
      },
      {
       "type": "input_value",
       "name": "OPERAND2",
       "shadow": {
        "type": "text",
        "fields": {
         "TEXT": ""
        },
        "quote": false
       }
      }
     ]
    }
   ]
  },
  {
   "type": "operator_equals",
   "cat": "operators",
   "colour": "#59C059",
   "icon": false,
   "shape": "boolean",
   "lines": [
    {
     "text": "%1 = %2",
     "args": [
      {
       "type": "input_value",
       "name": "OPERAND1",
       "shadow": {
        "type": "text",
        "fields": {
         "TEXT": ""
        },
        "quote": false
       }
      },
      {
       "type": "input_value",
       "name": "OPERAND2",
       "shadow": {
        "type": "text",
        "fields": {
         "TEXT": "50"
        },
        "quote": false
       }
      }
     ]
    }
   ]
  },
  {
   "type": "operator_and",
   "cat": "operators",
   "colour": "#59C059",
   "icon": false,
   "shape": "boolean",
   "lines": [
    {
     "text": "%1 与 %2",
     "args": [
      {
       "type": "input_value",
       "name": "OPERAND1"
      },
      {
       "type": "input_value",
       "name": "OPERAND2"
      }
     ]
    }
   ]
  },
  {
   "type": "operator_or",
   "cat": "operators",
   "colour": "#59C059",
   "icon": false,
   "shape": "boolean",
   "lines": [
    {
     "text": "%1 或 %2",
     "args": [
      {
       "type": "input_value",
       "name": "OPERAND1"
      },
      {
       "type": "input_value",
       "name": "OPERAND2"
      }
     ]
    }
   ]
  },
  {
   "type": "operator_not",
   "cat": "operators",
   "colour": "#59C059",
   "icon": false,
   "shape": "boolean",
   "lines": [
    {
     "text": "非 %1",
     "args": [
      {
       "type": "input_value",
       "name": "OPERAND"
      }
     ]
    }
   ]
  },
  {
   "type": "operator_join",
   "cat": "operators",
   "colour": "#59C059",
   "icon": false,
   "shape": "reporter",
   "lines": [
    {
     "text": "合并 %1 %2",
     "args": [
      {
       "type": "input_value",
       "name": "STRING1",
       "shadow": {
        "type": "text",
        "fields": {
         "TEXT": "苹果 "
        }
       }
      },
      {
       "type": "input_value",
       "name": "STRING2",
       "shadow": {
        "type": "text",
        "fields": {
         "TEXT": "香蕉"
        }
       }
      }
     ]
    }
   ]
  },
  {
   "type": "operator_letter_of",
   "cat": "operators",
   "colour": "#59C059",
   "icon": false,
   "shape": "reporter",
   "lines": [
    {
     "text": "%1 的第 %2 个字符",
     "args": [
      {
       "type": "input_value",
       "name": "STRING",
       "shadow": {
        "type": "text",
        "fields": {
         "TEXT": "苹果"
        }
       }
      },
      {
       "type": "input_value",
       "name": "LETTER",
       "shadow": {
        "type": "math_whole_number",
        "fields": {
         "NUM": "1"
        }
       }
      }
     ]
    }
   ]
  },
  {
   "type": "operator_length",
   "cat": "operators",
   "colour": "#59C059",
   "icon": false,
   "shape": "reporter",
   "lines": [
    {
     "text": "%1 的字符数",
     "args": [
      {
       "type": "input_value",
       "name": "STRING",
       "shadow": {
        "type": "text",
        "fields": {
         "TEXT": "苹果"
        }
       }
      }
     ]
    }
   ]
  },
  {
   "type": "operator_contains",
   "cat": "operators",
   "colour": "#59C059",
   "icon": false,
   "shape": "boolean",
   "lines": [
    {
     "text": "%1 包含 %2",
     "args": [
      {
       "type": "input_value",
       "name": "STRING1",
       "shadow": {
        "type": "text",
        "fields": {
         "TEXT": "苹果"
        }
       }
      },
      {
       "type": "input_value",
       "name": "STRING2",
       "shadow": {
        "type": "text",
        "fields": {
         "TEXT": "果"
        }
       }
      }
     ]
    }
   ]
  },
  {
   "type": "operator_textsrobotseven",
   "cat": "operators",
   "colour": "#59C059",
   "icon": false,
   "shape": "reporter",
   "lines": [
    {
     "text": "%1 获取 %2 %3 个字符到 %4 %5 个字符",
     "args": [
      {
       "type": "input_value",
       "name": "STRING1",
       "shadow": {
        "type": "text",
        "fields": {
         "TEXT": "apple"
        }
       }
      },
      {
       "type": "field_dropdown",
       "name": "LIST1",
       "options": [
        [
         "第",
         "first"
        ],
        [
         "倒数第",
         "last"
        ]
       ],
       "value": "first"
      },
      {
       "type": "input_value",
       "name": "STRING2",
       "shadow": {
        "type": "math_number",
        "fields": {
         "NUM": "1"
        }
       }
      },
      {
       "type": "field_dropdown",
       "name": "LIST2",
       "options": [
        [
         "第",
         "first"
        ],
        [
         "倒数第",
         "last"
        ]
       ],
       "value": "first"
      },
      {
       "type": "input_value",
       "name": "STRING3",
       "shadow": {
        "type": "math_number",
        "fields": {
         "NUM": "2"
        }
       }
      }
     ]
    }
   ]
  },
  {
   "type": "operator_textsrobotsevenNew",
   "cat": "operators",
   "colour": "#59C059",
   "icon": false,
   "shape": "reporter",
   "lines": [
    {
     "text": "查找 %1 在 %2 中 %3 出现位置",
     "args": [
      {
       "type": "input_value",
       "name": "STRING1",
       "shadow": {
        "type": "text",
        "fields": {
         "TEXT": "ap"
        }
       }
      },
      {
       "type": "input_value",
       "name": "STRING2",
       "shadow": {
        "type": "text",
        "fields": {
         "TEXT": "apple"
        }
       }
      },
      {
       "type": "field_dropdown",
       "name": "LIST1",
       "options": [
        [
         "首次",
         "first"
        ],
        [
         "最后一次",
         "last"
        ]
       ],
       "value": "first"
      }
     ]
    }
   ]
  },
  {
   "type": "operator_mod",
   "cat": "operators",
   "colour": "#59C059",
   "icon": false,
   "shape": "reporter",
   "lines": [
    {
     "text": "%1 除以 %2 的余数",
     "args": [
      {
       "type": "input_value",
       "name": "NUM1",
       "shadow": {
        "type": "math_number",
        "fields": {
         "NUM": ""
        }
       }
      },
      {
       "type": "input_value",
       "name": "NUM2",
       "shadow": {
        "type": "math_number",
        "fields": {
         "NUM": ""
        }
       }
      }
     ]
    }
   ]
  },
  {
   "type": "operator_round",
   "cat": "operators",
   "colour": "#59C059",
   "icon": false,
   "shape": "reporter",
   "lines": [
    {
     "text": "四舍五入 %1",
     "args": [
      {
       "type": "input_value",
       "name": "NUM",
       "shadow": {
        "type": "math_number",
        "fields": {
         "NUM": ""
        }
       }
      }
     ]
    }
   ]
  },
  {
   "type": "operator_stringToNum",
   "cat": "operators",
   "colour": "#59C059",
   "icon": false,
   "shape": "reporter",
   "lines": [
    {
     "text": "将字符串 %1 转换为 %2",
     "args": [
      {
       "type": "input_value",
       "name": "STRING",
       "shadow": {
        "type": "text",
        "fields": {
         "TEXT": "123"
        }
       }
      },
      {
       "type": "field_dropdown",
       "name": "TYPE",
       "options": [
        [
         "整数",
         "Integer"
        ],
        [
         "小数",
         "Decimal"
        ]
       ],
       "value": "Integer"
      }
     ]
    }
   ]
  },
  {
   "type": "operator_mathop",
   "cat": "operators",
   "colour": "#59C059",
   "icon": false,
   "shape": "reporter",
   "lines": [
    {
     "text": "%1 %2",
     "args": [
      {
       "type": "field_dropdown",
       "name": "OPERATOR",
       "options": [
        [
         "绝对值",
         "abs"
        ],
        [
         "向下取整",
         "floor"
        ],
        [
         "向上取整",
         "ceiling"
        ],
        [
         "平方根",
         "sqrt"
        ],
        [
         "正弦(sin)",
         "sin"
        ],
        [
         "余弦(cos)",
         "cos"
        ],
        [
         "正切(tan)",
         "tan"
        ],
        [
         " 反正弦(asin)",
         "asin"
        ],
        [
         " 反余弦(acos)",
         "acos"
        ],
        [
         "反正切(atan)",
         "atan"
        ],
        [
         "ln",
         "ln"
        ],
        [
         "log",
         "log"
        ],
        [
         "e ^",
         "e ^"
        ],
        [
         "10 ^",
         "10 ^"
        ]
       ],
       "value": "abs"
      },
      {
       "type": "input_value",
       "name": "NUM",
       "shadow": {
        "type": "math_number",
        "fields": {
         "NUM": ""
        }
       }
      }
     ]
    }
   ]
  },
  {
   "type": "operator_numToASCII",
   "cat": "operators",
   "colour": "#59C059",
   "icon": false,
   "shape": "reporter",
   "lines": [
    {
     "text": "将数字 %1 转换为 ASCII字符串",
     "args": [
      {
       "type": "input_value",
       "name": "NUM",
       "shadow": {
        "type": "math_number",
        "fields": {
         "NUM": "0"
        }
       }
      }
     ]
    }
   ]
  },
  {
   "type": "operator_stringToASCII",
   "cat": "operators",
   "colour": "#59C059",
   "icon": false,
   "shape": "reporter",
   "lines": [
    {
     "text": "将字符 %1 转换为 ASCII数值",
     "args": [
      {
       "type": "input_value",
       "name": "STRING",
       "shadow": {
        "type": "text",
        "fields": {
         "TEXT": "a"
        }
       }
      }
     ]
    }
   ]
  },
  {
   "type": "operator_numToString",
   "cat": "operators",
   "colour": "#59C059",
   "icon": false,
   "shape": "reporter",
   "lines": [
    {
     "text": "将数字 %1 转换为字符串",
     "args": [
      {
       "type": "input_value",
       "name": "NUM",
       "shadow": {
        "type": "math_number",
        "fields": {
         "NUM": "132"
        }
       }
      }
     ]
    }
   ]
  },
  {
   "type": "operator_map",
   "cat": "operators",
   "colour": "#59C059",
   "icon": false,
   "shape": "reporter",
   "lines": [
    {
     "text": "映射 %1 从[ %2 , %3 ] 到[ %4 , %5 ]",
     "args": [
      {
       "type": "input_value",
       "name": "VALUE",
       "shadow": {
        "type": "math_number",
        "fields": {
         "NUM": "0"
        }
       }
      },
      {
       "type": "input_value",
       "name": "FROMLOW",
       "shadow": {
        "type": "math_number",
        "fields": {
         "NUM": "0"
        }
       }
      },
      {
       "type": "input_value",
       "name": "FROMHIGH",
       "shadow": {
        "type": "math_number",
        "fields": {
         "NUM": "1023"
        }
       }
      },
      {
       "type": "input_value",
       "name": "TOLOW",
       "shadow": {
        "type": "math_number",
        "fields": {
         "NUM": "0"
        }
       }
      },
      {
       "type": "input_value",
       "name": "TOHIGH",
       "shadow": {
        "type": "math_number",
        "fields": {
         "NUM": "255"
        }
       }
      }
     ]
    }
   ]
  },
  {
   "type": "operator_constrain",
   "cat": "operators",
   "colour": "#59C059",
   "icon": false,
   "shape": "reporter",
   "lines": [
    {
     "text": "约束 %1 介于(最小值) %2 和(最大值) %3 之间",
     "args": [
      {
       "type": "input_value",
       "name": "VALUE",
       "shadow": {
        "type": "math_number",
        "fields": {
         "NUM": "0"
        }
       }
      },
      {
       "type": "input_value",
       "name": "MIN",
       "shadow": {
        "type": "math_number",
        "fields": {
         "NUM": "0"
        }
       }
      },
      {
       "type": "input_value",
       "name": "MAX",
       "shadow": {
        "type": "math_number",
        "fields": {
         "NUM": "100"
        }
       }
      }
     ]
    }
   ]
  },
  {
   "type": "data_variable",
   "cat": "variables",
   "colour": "#FF8C1A",
   "icon": false,
   "shape": "reporter",
   "lines": [
    {
     "text": "变量 %1",
     "args": [
      {
       "type": "field_variable",
       "name": "VARIABLE",
       "variableTypes": [
        ""
       ],
       "value": "my variable"
      }
     ]
    }
   ]
  },
  {
   "type": "data_setvariableto",
   "cat": "variables",
   "colour": "#FF8C1A",
   "icon": false,
   "shape": "stack",
   "lines": [
    {
     "text": "设置 %1 的值为 %2",
     "args": [
      {
       "type": "field_variable",
       "name": "VARIABLE",
       "variableTypes": [
        ""
       ],
       "value": "my variable"
      },
      {
       "type": "input_value",
       "name": "VALUE",
       "shadow": {
        "type": "math_number",
        "fields": {
         "NUM": "0"
        }
       }
      }
     ]
    }
   ]
  },
  {
   "type": "data_changevariableby",
   "cat": "variables",
   "colour": "#FF8C1A",
   "icon": false,
   "shape": "stack",
   "lines": [
    {
     "text": "将 %1 增加 %2",
     "args": [
      {
       "type": "field_variable",
       "name": "VARIABLE",
       "variableTypes": [
        ""
       ],
       "value": "my variable"
      },
      {
       "type": "input_value",
       "name": "VALUE",
       "shadow": {
        "type": "math_number",
        "fields": {
         "NUM": "1"
        }
       }
      }
     ]
    }
   ]
  },
  {
   "type": "data_string",
   "cat": "variables",
   "colour": "#921AFF",
   "icon": false,
   "shape": "reporter",
   "lines": [
    {
     "text": "变量 %1",
     "args": [
      {
       "type": "field_variable",
       "name": "STRING",
       "variableTypes": [
        "string"
       ],
       "value": "my string variable"
      }
     ]
    }
   ]
  },
  {
   "type": "data_setStringto",
   "cat": "variables",
   "colour": "#921AFF",
   "icon": false,
   "shape": "stack",
   "lines": [
    {
     "text": "设置 %1 的值为 %2",
     "args": [
      {
       "type": "field_variable",
       "name": "STRING",
       "variableTypes": [
        "string"
       ],
       "value": "my string variable"
      },
      {
       "type": "input_value",
       "name": "VALUE",
       "shadow": {
        "type": "text",
        "fields": {
         "TEXT": "hello"
        }
       }
      }
     ]
    }
   ]
  },
  {
   "type": "data_stringStartWith",
   "cat": "variables",
   "colour": "#921AFF",
   "icon": false,
   "shape": "boolean",
   "lines": [
    {
     "text": "%1 以 %2 字符串开始",
     "args": [
      {
       "type": "field_variable",
       "name": "STRING",
       "variableTypes": [
        "string"
       ],
       "value": "my string variable"
      },
      {
       "type": "input_value",
       "name": "VALUE",
       "shadow": {
        "type": "text",
        "fields": {
         "TEXT": "a"
        }
       }
      }
     ]
    }
   ]
  },
  {
   "type": "data_listcontents",
   "cat": "variables",
   "colour": "#FF661A",
   "icon": false,
   "shape": "reporter",
   "lines": [
    {
     "text": "列表 %1",
     "args": [
      {
       "type": "field_variable",
       "name": "LIST",
       "variableTypes": [
        "list"
       ],
       "value": "my list"
      }
     ]
    }
   ]
  },
  {
   "type": "data_addtolist",
   "cat": "variables",
   "colour": "#FF661A",
   "icon": false,
   "shape": "stack",
   "lines": [
    {
     "text": "将 %1 加入 %2",
     "args": [
      {
       "type": "input_value",
       "name": "ITEM",
       "shadow": {
        "type": "text",
        "fields": {
         "TEXT": "东西"
        }
       }
      },
      {
       "type": "field_variable",
       "name": "LIST",
       "variableTypes": [
        "list"
       ],
       "value": "my list"
      }
     ]
    }
   ]
  },
  {
   "type": "data_deleteoflist",
   "cat": "variables",
   "colour": "#FF661A",
   "icon": false,
   "shape": "stack",
   "lines": [
    {
     "text": "删除 %1 的第 %2 项",
     "args": [
      {
       "type": "field_variable",
       "name": "LIST",
       "variableTypes": [
        "list"
       ],
       "value": "my list"
      },
      {
       "type": "input_value",
       "name": "INDEX",
       "shadow": {
        "type": "math_integer",
        "fields": {
         "NUM": "1"
        }
       }
      }
     ]
    }
   ]
  },
  {
   "type": "data_deletealloflist",
   "cat": "variables",
   "colour": "#FF661A",
   "icon": false,
   "shape": "stack",
   "lines": [
    {
     "text": "删除 %1 的全部项目",
     "args": [
      {
       "type": "field_variable",
       "name": "LIST",
       "variableTypes": [
        "list"
       ],
       "value": "my list"
      }
     ]
    }
   ]
  },
  {
   "type": "data_insertatlist",
   "cat": "variables",
   "colour": "#FF661A",
   "icon": false,
   "shape": "stack",
   "lines": [
    {
     "text": "在 %1 的第 %2 项前插入 %3",
     "args": [
      {
       "type": "field_variable",
       "name": "LIST",
       "variableTypes": [
        "list"
       ],
       "value": "my list"
      },
      {
       "type": "input_value",
       "name": "INDEX",
       "shadow": {
        "type": "math_integer",
        "fields": {
         "NUM": "1"
        }
       }
      },
      {
       "type": "input_value",
       "name": "ITEM",
       "shadow": {
        "type": "text",
        "fields": {
         "TEXT": "东西"
        }
       }
      }
     ]
    }
   ]
  },
  {
   "type": "data_replaceitemoflist",
   "cat": "variables",
   "colour": "#FF661A",
   "icon": false,
   "shape": "stack",
   "lines": [
    {
     "text": "将 %1 的第 %2 项替换为 %3",
     "args": [
      {
       "type": "field_variable",
       "name": "LIST",
       "variableTypes": [
        "list"
       ],
       "value": "my list"
      },
      {
       "type": "input_value",
       "name": "INDEX",
       "shadow": {
        "type": "math_integer",
        "fields": {
         "NUM": "1"
        }
       }
      },
      {
       "type": "input_value",
       "name": "ITEM",
       "shadow": {
        "type": "text",
        "fields": {
         "TEXT": "东西"
        }
       }
      }
     ]
    }
   ]
  },
  {
   "type": "data_itemoflist",
   "cat": "variables",
   "colour": "#FF661A",
   "icon": false,
   "shape": "reporter",
   "lines": [
    {
     "text": "%1 的第 %2 项",
     "args": [
      {
       "type": "field_variable",
       "name": "LIST",
       "variableTypes": [
        "list"
       ],
       "value": "my list"
      },
      {
       "type": "input_value",
       "name": "INDEX",
       "shadow": {
        "type": "math_integer",
        "fields": {
         "NUM": "1"
        }
       }
      }
     ]
    }
   ]
  },
  {
   "type": "data_itemnumoflist",
   "cat": "variables",
   "colour": "#FF661A",
   "icon": false,
   "shape": "reporter",
   "lines": [
    {
     "text": "%1 中第一个 %2 的编号",
     "args": [
      {
       "type": "field_variable",
       "name": "LIST",
       "variableTypes": [
        "list"
       ],
       "value": "my list"
      },
      {
       "type": "input_value",
       "name": "ITEM",
       "shadow": {
        "type": "text",
        "fields": {
         "TEXT": "东西"
        }
       }
      }
     ]
    }
   ]
  },
  {
   "type": "data_lengthoflist",
   "cat": "variables",
   "colour": "#FF661A",
   "icon": false,
   "shape": "reporter",
   "lines": [
    {
     "text": "%1 的项目数",
     "args": [
      {
       "type": "field_variable",
       "name": "LIST",
       "variableTypes": [
        "list"
       ],
       "value": "my list"
      }
     ]
    }
   ]
  },
  {
   "type": "data_listcontainsitem",
   "cat": "variables",
   "colour": "#FF661A",
   "icon": false,
   "shape": "boolean",
   "lines": [
    {
     "text": "%1 包含 %2",
     "args": [
      {
       "type": "field_variable",
       "name": "LIST",
       "variableTypes": [
        "list"
       ],
       "value": "my list"
      },
      {
       "type": "input_value",
       "name": "ITEM",
       "shadow": {
        "type": "text",
        "fields": {
         "TEXT": "东西"
        }
       }
      }
     ]
    }
   ]
  },
  {
   "type": "dev-DFRobot-handpyEsp32@0.0.6_start",
   "cat": "handpy",
   "colour": "#87C482",
   "icon": true,
   "shape": "hat",
   "lines": [
    {
     "text": "ESP32 主程序",
     "args": []
    }
   ]
  },
  {
   "type": "dev-DFRobot-handpyEsp32@0.0.6_esp32_buttonPress",
   "cat": "handpy",
   "colour": "#87C482",
   "icon": true,
   "shape": "hat",
   "lines": [
    {
     "text": "当 %1 按钮 %2",
     "args": [
      {
       "type": "field_dropdown",
       "name": "REFERENCE",
       "options": [
        [
         "A",
         "A"
        ],
        [
         "B",
         "B"
        ],
        [
         "A+B",
         "A+B"
        ]
       ],
       "value": "A"
      },
      {
       "type": "field_dropdown",
       "name": "BUTTONSTATE",
       "options": [
        [
         "按下",
         "Pressed"
        ],
        [
         "松开",
         "Loosened"
        ]
       ],
       "value": "Pressed"
      }
     ]
    }
   ]
  },
  {
   "type": "dev-DFRobot-handpyEsp32@0.0.6_esp32_padConnected",
   "cat": "handpy",
   "colour": "#87C482",
   "icon": true,
   "shape": "hat",
   "lines": [
    {
     "text": "当触摸按键 %1 被 %2",
     "args": [
      {
       "type": "field_dropdown",
       "name": "REFERENCE",
       "options": [
        [
         "P",
         "27"
        ],
        [
         "Y",
         "14"
        ],
        [
         "T",
         "12"
        ],
        [
         "H",
         "13"
        ],
        [
         "O",
         "15"
        ],
        [
         "N",
         "4"
        ]
       ],
       "value": "27"
      },
      {
       "type": "field_dropdown",
       "name": "TOUCHSTATE",
       "options": [
        [
         "接通",
         "Connected"
        ],
        [
         "断开",
         "Disconnected"
        ]
       ],
       "value": "Connected"
      }
     ]
    }
   ]
  },
  {
   "type": "dev-DFRobot-handpyEsp32@0.0.6_onGesture",
   "cat": "handpy",
   "colour": "#87C482",
   "icon": true,
   "shape": "hat",
   "lines": [
    {
     "text": "当 %1",
     "args": [
      {
       "type": "field_dropdown",
       "name": "GESTURE",
       "options": [
        [
         "向前倾斜",
         "TiltForward"
        ],
        [
         "向后倾斜",
         "TiltBack"
        ],
        [
         "向左倾斜",
         "TiltLeft"
        ],
        [
         "向右倾斜",
         "TiltRight"
        ],
        [
         "屏幕朝上",
         "ScreenUp"
        ],
        [
         "屏幕朝下",
         "ScreenDown"
        ],
        [
         "摇一摇",
         "Shake"
        ]
       ],
       "value": "TiltForward"
      }
     ]
    }
   ]
  },
  {
   "type": "dev-DFRobot-handpyEsp32@0.0.6_esp32_serialSend",
   "cat": "handpy",
   "colour": "#87C482",
   "icon": true,
   "shape": "stack",
   "lines": [
    {
     "text": "%1 %2 %3 %4",
     "args": [
      {
       "type": "field_dropdown",
       "name": "SERIAL",
       "options": [
        [
         "串口0",
         "SerialPort0"
        ],
        [
         "串口1",
         "SerialPort1"
        ],
        [
         "串口2",
         "SerialPort2"
        ]
       ],
       "value": "SerialPort0"
      },
      {
       "type": "field_dropdown",
       "name": "WAY",
       "options": [
        [
         "字符串输出",
         "string output"
        ],
        [
         "原始输出",
         "original output"
        ],
        [
         "十六进制输出",
         "HEX output"
        ]
       ],
       "value": "string output"
      },
      {
       "type": "input_value",
       "name": "STRING",
       "shadow": {
        "type": "text",
        "fields": {
         "TEXT": "hello"
        },
        "quote": false
       }
      },
      {
       "type": "field_dropdown",
       "name": "LINE",
       "options": [
        [
         "换行",
         "Wrap"
        ],
        [
         "不换行",
         "No-Wrap"
        ]
       ],
       "value": "Wrap"
      }
     ]
    }
   ]
  },
  {
   "type": "dev-DFRobot-handpyEsp32@0.0.6_esp32_havenumberread",
   "cat": "handpy",
   "colour": "#87C482",
   "icon": true,
   "shape": "boolean",
   "lines": [
    {
     "text": "%1 有数据可读？",
     "args": [
      {
       "type": "field_dropdown",
       "name": "SERIAL",
       "options": [
        [
         "串口0",
         "SerialPort0"
        ],
        [
         "串口1",
         "SerialPort1"
        ],
        [
         "串口2",
         "SerialPort2"
        ]
       ],
       "value": "SerialPort0"
      }
     ]
    }
   ]
  },
  {
   "type": "dev-DFRobot-handpyEsp32@0.0.6_esp32_readserialdata",
   "cat": "handpy",
   "colour": "#87C482",
   "icon": true,
   "shape": "reporter",
   "lines": [
    {
     "text": "读取 %1 数据",
     "args": [
      {
       "type": "field_dropdown",
       "name": "SERIAL",
       "options": [
        [
         "串口0",
         "SerialPort0"
        ],
        [
         "串口1",
         "SerialPort1"
        ],
        [
         "串口2",
         "SerialPort2"
        ]
       ],
       "value": "SerialPort0"
      }
     ]
    }
   ]
  },
  {
   "type": "dev-DFRobot-handpyEsp32@0.0.6_esp32_readserialIntfloat",
   "cat": "handpy",
   "colour": "#87C482",
   "icon": true,
   "shape": "reporter",
   "lines": [
    {
     "text": "从 %1 读取一个 %2",
     "args": [
      {
       "type": "field_dropdown",
       "name": "SERIAL",
       "options": [
        [
         "串口0",
         "SerialPort0"
        ],
        [
         "串口1",
         "SerialPort1"
        ],
        [
         "串口2",
         "SerialPort2"
        ]
       ],
       "value": "SerialPort0"
      },
      {
       "type": "field_dropdown",
       "name": "READTYPE",
       "options": [
        [
         "整数",
         "int"
        ],
        [
         "小数",
         "float"
        ],
        [
         "字符串",
         "string"
        ]
       ],
       "value": "int"
      }
     ]
    }
   ]
  },
  {
   "type": "dev-DFRobot-handpyEsp32@0.0.6_esp32_getNumOfFloat",
   "cat": "handpy",
   "colour": "#87C482",
   "icon": true,
   "shape": "reporter",
   "lines": [
    {
     "text": "%1 保留 %2 位小数",
     "args": [
      {
       "type": "input_value",
       "name": "TARGET",
       "shadow": {
        "type": "math_number",
        "fields": {
         "NUM": "123.123"
        }
       }
      },
      {
       "type": "input_value",
       "name": "NNUMS",
       "shadow": {
        "type": "math_slider",
        "fields": {
         "NUM": "0"
        },
        "min": 0,
        "max": 8
       }
      }
     ]
    }
   ]
  },
  {
   "type": "dev-DFRobot-handpyEsp32@0.0.6_esp32_setSerial",
   "cat": "handpy",
   "colour": "#87C482",
   "icon": true,
   "shape": "stack",
   "lines": [
    {
     "text": "设置 串口0 波特率 %1",
     "args": [
      {
       "type": "field_dropdown",
       "name": "BAUD",
       "options": [
        [
         "1200",
         "1200"
        ],
        [
         "2400",
         "2400"
        ],
        [
         "4800",
         "4800"
        ],
        [
         "9600",
         "9600"
        ],
        [
         "19200",
         "19200"
        ],
        [
         "38400",
         "38400"
        ],
        [
         "57600",
         "57600"
        ],
        [
         "76800",
         "76800"
        ],
        [
         "115200",
         "115200"
        ]
       ],
       "value": "9600"
      }
     ]
    }
   ]
  },
  {
   "type": "dev-DFRobot-handpyEsp32@0.0.6_esp32_seriaRemapping",
   "cat": "handpy",
   "colour": "#87C482",
   "icon": true,
   "shape": "stack",
   "lines": [
    {
     "text": "设置 %1 Rx %2 Tx %3 波特率为 %4",
     "args": [
      {
       "type": "field_dropdown",
       "name": "SERIAL",
       "options": [
        [
         "串口1",
         "SerialPort1"
        ],
        [
         "串口2",
         "SerialPort2"
        ]
       ],
       "value": "SerialPort1"
      },
      {
       "type": "field_dropdown",
       "name": "RX",
       "options": [
        [
         "P0",
         "P0"
        ],
        [
         "P1",
         "P1"
        ],
        [
         "P2",
         "P2"
        ],
        [
         "P3",
         "P3"
        ],
        [
         "P4",
         "P4"
        ],
        [
         "P5",
         "P5"
        ],
        [
         "P6  (板载蜂鸣器)",
         "P6"
        ],
        [
         "P7",
         "P7"
        ],
        [
         "P8",
         "P8"
        ],
        [
         "P9",
         "P9"
        ],
        [
         "P10",
         "P10"
        ],
        [
         "P11",
         "P11"
        ],
        [
         "P13",
         "P13"
        ],
        [
         "P14",
         "P14"
        ],
        [
         "P15",
         "P15"
        ],
        [
         "P16",
         "P16"
        ]
       ],
       "value": "P0"
      },
      {
       "type": "field_dropdown",
       "name": "TX",
       "options": [
        [
         "P0",
         "P0"
        ],
        [
         "P1",
         "P1"
        ],
        [
         "P5",
         "P5"
        ],
        [
         "P6  (板载蜂鸣器)",
         "P6"
        ],
        [
         "P7",
         "P7"
        ],
        [
         "P8",
         "P8"
        ],
        [
         "P9",
         "P9"
        ],
        [
         "P11",
         "P11"
        ],
        [
         "P13",
         "P13"
        ],
        [
         "P14",
         "P14"
        ],
        [
         "P15",
         "P15"
        ],
        [
         "P16",
         "P16"
        ]
       ],
       "value": "P1"
      },
      {
       "type": "field_dropdown",
       "name": "BAUD",
       "options": [
        [
         "1200",
         "1200"
        ],
        [
         "2400",
         "2400"
        ],
        [
         "4800",
         "4800"
        ],
        [
         "9600",
         "9600"
        ],
        [
         "19200",
         "19200"
        ],
        [
         "38400",
         "38400"
        ],
        [
         "57600",
         "57600"
        ],
        [
         "76800",
         "76800"
        ],
        [
         "115200",
         "115200"
        ]
       ],
       "value": "9600"
      }
     ]
    }
   ]
  },
  {
   "type": "dev-DFRobot-handpyEsp32@0.0.6_esp32_set_pinMode",
   "cat": "handpy",
   "colour": "#87C482",
   "icon": true,
   "shape": "stack",
   "lines": [
    {
     "text": "设置引脚 %1 模式 %2",
     "args": [
      {
       "type": "field_dropdown",
       "name": "PIN",
       "options": [
        [
         "P0",
         "P0"
        ],
        [
         "P1",
         "P1"
        ],
        [
         "P2",
         "P2"
        ],
        [
         "P5",
         "P5"
        ],
        [
         "P7",
         "P7"
        ],
        [
         "P8",
         "P8"
        ],
        [
         "P9",
         "P9"
        ],
        [
         "P11",
         "P11"
        ],
        [
         "P13",
         "P13"
        ],
        [
         "P14",
         "P14"
        ],
        [
         "P15",
         "P15"
        ],
        [
         "P16",
         "P16"
        ],
        [
         "P19",
         "P19"
        ],
        [
         "P20",
         "P20"
        ],
        [
         "P",
         "27"
        ],
        [
         "Y",
         "14"
        ],
        [
         "T",
         "12"
        ],
        [
         "H",
         "13"
        ],
        [
         "O",
         "15"
        ],
        [
         "N",
         "4"
        ]
       ],
       "value": "P0"
      },
      {
       "type": "field_dropdown",
       "name": "PINMODE",
       "options": [
        [
         "OUTPUT",
         "OUTPUT"
        ],
        [
         "INPUT",
         "INPUT"
        ],
        [
         "INPUT_PULLUP",
         "INPUT_PULLUP"
        ]
       ],
       "value": "OUTPUT"
      }
     ]
    }
   ]
  },
  {
   "type": "dev-DFRobot-handpyEsp32@0.0.6_esp32_digitalRead",
   "cat": "handpy",
   "colour": "#87C482",
   "icon": true,
   "shape": "boolean",
   "lines": [
    {
     "text": "读取数字引脚 %1",
     "args": [
      {
       "type": "field_dropdown",
       "name": "PIN",
       "options": [
        [
         "P0",
         "P0"
        ],
        [
         "P1",
         "P1"
        ],
        [
         "P2",
         "P2"
        ],
        [
         "P5",
         "P5"
        ],
        [
         "P7",
         "P7"
        ],
        [
         "P8",
         "P8"
        ],
        [
         "P9",
         "P9"
        ],
        [
         "P11",
         "P11"
        ],
        [
         "P13",
         "P13"
        ],
        [
         "P14",
         "P14"
        ],
        [
         "P15",
         "P15"
        ],
        [
         "P16",
         "P16"
        ],
        [
         "P19",
         "P19"
        ],
        [
         "P20",
         "P20"
        ],
        [
         "P",
         "27"
        ],
        [
         "Y",
         "14"
        ],
        [
         "T",
         "12"
        ],
        [
         "H",
         "13"
        ],
        [
         "O",
         "15"
        ],
        [
         "N",
         "4"
        ]
       ],
       "value": "P0"
      }
     ]
    }
   ]
  },
  {
   "type": "dev-DFRobot-handpyEsp32@0.0.6_esp32_analogRead",
   "cat": "handpy",
   "colour": "#87C482",
   "icon": true,
   "shape": "reporter",
   "lines": [
    {
     "text": "读取模拟引脚 %1",
     "args": [
      {
       "type": "field_dropdown",
       "name": "PIN",
       "options": [
        [
         "P0",
         "P0"
        ],
        [
         "P1",
         "P1"
        ],
        [
         "P2",
         "P2"
        ],
        [
         "P3",
         "P3"
        ]
       ],
       "value": "P0"
      }
     ]
    }
   ]
  },
  {
   "type": "dev-DFRobot-handpyEsp32@0.0.6_esp32_digitalWrite",
   "cat": "handpy",
   "colour": "#87C482",
   "icon": true,
   "shape": "stack",
   "lines": [
    {
     "text": "设置数字引脚 %1 输出 %2",
     "args": [
      {
       "type": "field_dropdown",
       "name": "PIN",
       "options": [
        [
         "P0",
         "P0"
        ],
        [
         "P1",
         "P1"
        ],
        [
         "P5",
         "P5"
        ],
        [
         "P7",
         "P7"
        ],
        [
         "P8",
         "P8"
        ],
        [
         "P9",
         "P9"
        ],
        [
         "P11",
         "P11"
        ],
        [
         "P13",
         "P13"
        ],
        [
         "P14",
         "P14"
        ],
        [
         "P15",
         "P15"
        ],
        [
         "P16",
         "P16"
        ],
        [
         "P19",
         "P19"
        ],
        [
         "P20",
         "P20"
        ],
        [
         "P",
         "27"
        ],
        [
         "Y",
         "14"
        ],
        [
         "T",
         "12"
        ],
        [
         "H",
         "13"
        ],
        [
         "O",
         "15"
        ],
        [
         "N",
         "4"
        ]
       ],
       "value": "P0"
      },
      {
       "type": "field_dropdown",
       "name": "VALUE",
       "options": [
        [
         "低电平",
         "LOW"
        ],
        [
         "高电平",
         "HIGH"
        ]
       ],
       "value": "LOW"
      }
     ]
    }
   ]
  },
  {
   "type": "dev-DFRobot-handpyEsp32@0.0.6_esp32_analogWrite",
   "cat": "handpy",
   "colour": "#87C482",
   "icon": true,
   "shape": "stack",
   "lines": [
    {
     "text": "设置模拟引脚 %1 输出(PWM) %2",
     "args": [
      {
       "type": "field_dropdown",
       "name": "PIN",
       "options": [
        [
         "P0",
         "P0"
        ],
        [
         "P1",
         "P1"
        ],
        [
         "P5",
         "P5"
        ],
        [
         "P7",
         "P7"
        ],
        [
         "P8",
         "P8"
        ],
        [
         "P9",
         "P9"
        ],
        [
         "P11",
         "P11"
        ],
        [
         "P13",
         "P13"
        ],
        [
         "P14",
         "P14"
        ],
        [
         "P15",
         "P15"
        ],
        [
         "P16",
         "P16"
        ],
        [
         "P19",
         "P19"
        ],
        [
         "P20",
         "P20"
        ],
        [
         "P",
         "27"
        ],
        [
         "Y",
         "14"
        ],
        [
         "T",
         "12"
        ],
        [
         "H",
         "13"
        ],
        [
         "O",
         "15"
        ],
        [
         "N",
         "4"
        ]
       ],
       "value": "P0"
      },
      {
       "type": "input_value",
       "name": "VALUE",
       "shadow": {
        "type": "math_slider",
        "fields": {
         "NUM": "512"
        },
        "min": 0,
        "max": 1023
       }
      }
     ]
    }
   ]
  },
  {
   "type": "dev-DFRobot-handpyEsp32@0.0.6_esp32_timer",
   "cat": "handpy",
   "colour": "#87C482",
   "icon": true,
   "shape": "reporter",
   "lines": [
    {
     "text": "系统运行时间(ms)",
     "args": []
    }
   ]
  },
  {
   "type": "dev-DFRobot-handpyEsp32@0.0.6_esp32_btnIsPressed",
   "cat": "handpy_sensor",
   "colour": "#87C482",
   "icon": true,
   "shape": "boolean",
   "lines": [
    {
     "text": "按钮 %1 被 %2 ？",
     "args": [
      {
       "type": "field_dropdown",
       "name": "BUTTON",
       "options": [
        [
         "A",
         "A"
        ],
        [
         "B",
         "B"
        ],
        [
         "A+B",
         "A+B"
        ]
       ],
       "value": "A"
      },
      {
       "type": "field_dropdown",
       "name": "BUTTONSTATE",
       "options": [
        [
         "按下",
         "Pressed"
        ],
        [
         "松开",
         "Loosened"
        ]
       ],
       "value": "Pressed"
      }
     ]
    }
   ]
  },
  {
   "type": "dev-DFRobot-handpyEsp32@0.0.6_esp32_pinIsConnected",
   "cat": "handpy_sensor",
   "colour": "#87C482",
   "icon": true,
   "shape": "boolean",
   "lines": [
    {
     "text": "触摸按键 %1 被 %2 ？",
     "args": [
      {
       "type": "field_dropdown",
       "name": "PIN",
       "options": [
        [
         "P",
         "27"
        ],
        [
         "Y",
         "14"
        ],
        [
         "T",
         "12"
        ],
        [
         "H",
         "13"
        ],
        [
         "O",
         "15"
        ],
        [
         "N",
         "4"
        ]
       ],
       "value": "27"
      },
      {
       "type": "field_dropdown",
       "name": "TOUCHSTATE",
       "options": [
        [
         "接通",
         "Connected"
        ],
        [
         "断开",
         "Disconnected"
        ]
       ],
       "value": "Connected"
      }
     ]
    }
   ]
  },
  {
   "type": "dev-DFRobot-handpyEsp32@0.0.6_esp32_setTouchThreshold",
   "cat": "handpy_sensor",
   "colour": "#87C482",
   "icon": true,
   "shape": "stack",
   "lines": [
    {
     "text": "设置按键 %1 触摸阈值为 %2",
     "args": [
      {
       "type": "field_dropdown",
       "name": "PIN",
       "options": [
        [
         "P",
         "27"
        ],
        [
         "Y",
         "14"
        ],
        [
         "T",
         "12"
        ],
        [
         "H",
         "13"
        ],
        [
         "O",
         "15"
        ],
        [
         "N",
         "4"
        ],
        [
         "全部",
         "ALL"
        ]
       ],
       "value": "ALL"
      },
      {
       "type": "input_value",
       "name": "THRESHOLD",
       "shadow": {
        "type": "math_slider",
        "fields": {
         "NUM": "35"
        },
        "min": 0,
        "max": 80
       }
      }
     ]
    }
   ]
  },
  {
   "type": "dev-DFRobot-handpyEsp32@0.0.6_esp32_readTouchValue",
   "cat": "handpy_sensor",
   "colour": "#87C482",
   "icon": true,
   "shape": "reporter",
   "lines": [
    {
     "text": "读取按键 %1 触摸值",
     "args": [
      {
       "type": "field_dropdown",
       "name": "PIN",
       "options": [
        [
         "P",
         "27"
        ],
        [
         "Y",
         "14"
        ],
        [
         "T",
         "12"
        ],
        [
         "H",
         "13"
        ],
        [
         "O",
         "15"
        ],
        [
         "N",
         "4"
        ]
       ],
       "value": "27"
      }
     ]
    }
   ]
  },
  {
   "type": "dev-DFRobot-handpyEsp32@0.0.6_isGesture",
   "cat": "handpy_sensor",
   "colour": "#87C482",
   "icon": true,
   "shape": "boolean",
   "lines": [
    {
     "text": "当前姿态为 %1",
     "args": [
      {
       "type": "field_dropdown",
       "name": "GESTURE",
       "options": [
        [
         "向前倾斜",
         "TiltForward"
        ],
        [
         "向后倾斜",
         "TiltBack"
        ],
        [
         "向左倾斜",
         "TiltLeft"
        ],
        [
         "向右倾斜",
         "TiltRight"
        ],
        [
         "屏幕朝上",
         "ScreenUp"
        ],
        [
         "屏幕朝下",
         "ScreenDown"
        ],
        [
         "摇一摇",
         "Shake"
        ]
       ],
       "value": "TiltForward"
      }
     ]
    }
   ]
  },
  {
   "type": "dev-DFRobot-handpyEsp32@0.0.6_esp32_getSoundIntensity",
   "cat": "handpy_sensor",
   "colour": "#87C482",
   "icon": true,
   "shape": "reporter",
   "lines": [
    {
     "text": "读取麦克风声音强度",
     "args": []
    }
   ]
  },
  {
   "type": "dev-DFRobot-handpyEsp32@0.0.6_esp32_readBrightness",
   "cat": "handpy_sensor",
   "colour": "#87C482",
   "icon": true,
   "shape": "reporter",
   "lines": [
    {
     "text": "读取环境光强度",
     "args": []
    }
   ]
  },
  {
   "type": "dev-DFRobot-handpyEsp32@0.0.6_esp32_accelerationCalibration",
   "cat": "handpy_sensor",
   "colour": "#87C482",
   "icon": true,
   "shape": "stack",
   "lines": [
    {
     "text": "加速度校准 X轴偏移 %1 Y轴偏移 %2 Z轴偏移 %3 (m-g)",
     "args": [
      {
       "type": "input_value",
       "name": "XOFFSET",
       "shadow": {
        "type": "math_slider",
        "fields": {
         "NUM": "0"
        },
        "min": -1000,
        "max": 1000
       }
      },
      {
       "type": "input_value",
       "name": "YOFFSET",
       "shadow": {
        "type": "math_slider",
        "fields": {
         "NUM": "0"
        },
        "min": -1000,
        "max": 1000
       }
      },
      {
       "type": "input_value",
       "name": "ZOFFSET",
       "shadow": {
        "type": "math_slider",
        "fields": {
         "NUM": "0"
        },
        "min": -1000,
        "max": 1000
       }
      }
     ]
    }
   ]
  },
  {
   "type": "dev-DFRobot-handpyEsp32@0.0.6_esp32_acceleration",
   "cat": "handpy_sensor",
   "colour": "#87C482",
   "icon": true,
   "shape": "reporter",
   "lines": [
    {
     "text": "读取加速度的值(m-g) %1",
     "args": [
      {
       "type": "field_dropdown",
       "name": "TYPE",
       "options": [
        [
         "X",
         "X"
        ],
        [
         "Y",
         "Y"
        ],
        [
         "Z",
         "Z"
        ],
        [
         "强度",
         "strength"
        ]
       ],
       "value": "X"
      }
     ]
    }
   ]
  },
  {
   "type": "dev-DFRobot-handpyEsp32@0.0.6_esp32_displayInLine",
   "cat": "handpy_screen",
   "colour": "#87C482",
   "icon": true,
   "shape": "stack",
   "lines": [
    {
     "text": "屏幕显示文字 %1 在第 %2 行",
     "args": [
      {
       "type": "input_value",
       "name": "TEXT",
       "shadow": {
        "type": "text",
        "fields": {
         "TEXT": "Mind+"
        },
        "quote": true
       }
      },
      {
       "type": "input_value",
       "name": "LINE",
       "shadow": {
        "type": "dev-DFRobot-handpyEsp32@0.0.6_menu_oledLineMenu",
        "fields": {
         "oledLineMenu": "1"
        }
       }
      }
     ]
    }
   ]
  },
  {
   "type": "dev-DFRobot-handpyEsp32@0.0.6_esp32_displayClearInLine",
   "cat": "handpy_screen",
   "colour": "#87C482",
   "icon": true,
   "shape": "stack",
   "lines": [
    {
     "text": "屏幕显示清除第 %1 行",
     "args": [
      {
       "type": "input_value",
       "name": "LINE",
       "shadow": {
        "type": "dev-DFRobot-handpyEsp32@0.0.6_menu_oledLineMenu",
        "fields": {
         "oledLineMenu": "1"
        }
       }
      }
     ]
    }
   ]
  },
  {
   "type": "dev-DFRobot-handpyEsp32@0.0.6_esp32_displayInXY",
   "cat": "handpy_screen",
   "colour": "#87C482",
   "icon": true,
   "shape": "stack",
   "lines": [
    {
     "text": "屏幕显示文字 %1 在坐标 X: %2 Y: %3 预览 %4",
     "args": [
      {
       "type": "input_value",
       "name": "TEXT",
       "shadow": {
        "type": "text",
        "fields": {
         "TEXT": "Mind+"
        },
        "quote": true
       }
      },
      {
       "type": "input_value",
       "name": "X",
       "shadow": {
        "type": "math_slider",
        "fields": {
         "NUM": "42"
        },
        "min": 0,
        "max": 127
       }
      },
      {
       "type": "input_value",
       "name": "Y",
       "shadow": {
        "type": "math_slider",
        "fields": {
         "NUM": "22"
        },
        "min": 0,
        "max": 63
       }
      },
      {
       "type": "input_value",
       "name": "PREVIEW",
       "shadow": {
        "type": "text_preview",
        "fields": {}
       }
      }
     ]
    }
   ]
  },
  {
   "type": "dev-DFRobot-handpyEsp32@0.0.6_esp32_imageDrawing",
   "cat": "handpy_screen",
   "colour": "#87C482",
   "icon": true,
   "shape": "stack",
   "lines": [
    {
     "text": "屏幕显示图片 %1 在坐标 x: %2 y: %3",
     "args": [
      {
       "type": "input_value",
       "name": "IMAGE",
       "shadow": {
        "type": "img_preview",
        "fields": {
         "ESP32_IMG": "{\"imgSrc\": \"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAADIAAAAyCAYAAAAeP4ixAAACHUlEQVRoQ+2Y4ZLDIAiEk/d/6HTsnB1CwF3QeEmb+9l4yscCguuyLNsy6W/b7KPWde22oOwwBaRCaKO936NkU0CQseg7AzUNBIVPgUFrWkAPCCN3XcN4m1lzCUWKEV7o3CZHCsRXVC0LRN4pPUlewy2U7PpCixigc6A3J3S+0CA6NCIetcIqCoKcSIG0kpFJVEuNVvJLb2uHeQ6kQbIVx1ODAYk4EIJkPK49mu2vUPhJ2yBIrTgZRXrUQOfqSkiDHKrEX+stvea16c0budHCD1dE03sVhAlD2bZ87gAHRu6HCgaliJbZ8lQEAuWQd4HW8LbOokCsOySbwDrMmDyyQlafD0FaB0U64Mhay0moi3iDZJI0YtgZaw+KtCiQF84w0LvV0VlrBflvo5Gh1vddu3JnkF11e0AysXDC/3yq6s8pMqNERwvOrvuVinhDywkRAbdsOc5qVQ7ld4bnIUXj6Uj2fa4i1iLm0Jlrihq1ae0GkbE8QkEdKsgxdGhVam9Dr/NFBljfM3vdHkSGlZ5ZzGS/qiJNkNrGS7luDVKk0sTWfK3n7N5kjxYOa335bTdYeZtmEjKT+Ki4WC821TYXxJutq3LZAQgBsu9negSnQViwSKhZRjMPDbpivZ0rZ/Zo0+apwuzDGuwpOEyRVoggVRhQFIJTQJARI74/ICO8OHKPr1DEffvNPkCP9HBkr98BiXjlCmtlGb/tI7ZulV4HB1ENaBCwMgAAAABJRU5ErkJggg==\", \"imgAddr\": \"\", \"imgWidth\": \"50\", \"imgHeight\": \"50\"}"
        }
       }
      },
      {
       "type": "input_value",
       "name": "X",
       "shadow": {
        "type": "math_slider",
        "fields": {
         "NUM": "39"
        },
        "min": 0,
        "max": 127
       }
      },
      {
       "type": "input_value",
       "name": "Y",
       "shadow": {
        "type": "math_slider",
        "fields": {
         "NUM": "7"
        },
        "min": 0,
        "max": 63
       }
      }
     ]
    }
   ]
  },
  {
   "type": "dev-DFRobot-handpyEsp32@0.0.6_esp32_fillScreen",
   "cat": "handpy_screen",
   "colour": "#87C482",
   "icon": true,
   "shape": "stack",
   "lines": [
    {
     "text": "屏幕显示为 %1 (清屏)",
     "args": [
      {
       "type": "field_dropdown",
       "name": "COLOR",
       "options": [
        [
         "全黑",
         "Black"
        ],
        [
         "全白",
         "White"
        ]
       ],
       "value": "Black"
      }
     ]
    }
   ]
  },
  {
   "type": "dev-DFRobot-handpyEsp32@0.0.6_esp32_screenRotation",
   "cat": "handpy_screen",
   "colour": "#87C482",
   "icon": true,
   "shape": "stack",
   "lines": [
    {
     "text": "屏幕旋转到 %1 度",
     "args": [
      {
       "type": "field_dropdown",
       "name": "ROTATION",
       "options": [
        [
         "0",
         "0"
        ],
        [
         "180",
         "180"
        ]
       ],
       "value": "180"
      }
     ]
    }
   ]
  },
  {
   "type": "dev-DFRobot-handpyEsp32@0.0.6_esp32_pointDrawing",
   "cat": "handpy_screen",
   "colour": "#87C482",
   "icon": true,
   "shape": "stack",
   "lines": [
    {
     "text": "画点 x: %1 y: %2",
     "args": [
      {
       "type": "input_value",
       "name": "X",
       "shadow": {
        "type": "math_slider",
        "fields": {
         "NUM": "0"
        },
        "min": 0,
        "max": 127
       }
      },
      {
       "type": "input_value",
       "name": "Y",
       "shadow": {
        "type": "math_slider",
        "fields": {
         "NUM": "0"
        },
        "min": 0,
        "max": 63
       }
      }
     ]
    }
   ]
  },
  {
   "type": "dev-DFRobot-handpyEsp32@0.0.6_esp32_setLineWidth",
   "cat": "handpy_screen",
   "colour": "#87C482",
   "icon": true,
   "shape": "stack",
   "lines": [
    {
     "text": "设置线宽为 %1",
     "args": [
      {
       "type": "input_value",
       "name": "LINEWIDTH",
       "shadow": {
        "type": "math_slider",
        "fields": {
         "NUM": "1"
        },
        "min": 1,
        "max": 128
       }
      }
     ]
    }
   ]
  },
  {
   "type": "dev-DFRobot-handpyEsp32@0.0.6_esp32_lineDrawing",
   "cat": "handpy_screen",
   "colour": "#87C482",
   "icon": true,
   "shape": "stack",
   "lines": [
    {
     "text": "画线 起点x1: %1 y1: %2 终点x2: %3 y2: %4",
     "args": [
      {
       "type": "input_value",
       "name": "X1",
       "shadow": {
        "type": "math_slider",
        "fields": {
         "NUM": "0"
        },
        "min": 0,
        "max": 127
       }
      },
      {
       "type": "input_value",
       "name": "Y1",
       "shadow": {
        "type": "math_slider",
        "fields": {
         "NUM": "0"
        },
        "min": 0,
        "max": 63
       }
      },
      {
       "type": "input_value",
       "name": "X2",
       "shadow": {
        "type": "math_slider",
        "fields": {
         "NUM": "0"
        },
        "min": 0,
        "max": 127
       }
      },
      {
       "type": "input_value",
       "name": "Y2",
       "shadow": {
        "type": "math_slider",
        "fields": {
         "NUM": "0"
        },
        "min": 0,
        "max": 63
       }
      }
     ]
    }
   ]
  },
  {
   "type": "dev-DFRobot-handpyEsp32@0.0.6_esp32_circleDrawing",
   "cat": "handpy_screen",
   "colour": "#87C482",
   "icon": true,
   "shape": "stack",
   "lines": [
    {
     "text": "画圆 %1 圆心 x: %2 y: %3 半径: %4",
     "args": [
      {
       "type": "field_dropdown",
       "name": "FILL",
       "options": [
        [
         "填充",
         "FILL"
        ],
        [
         "不填充",
         "NOTFILL"
        ]
       ],
       "value": "FILL"
      },
      {
       "type": "input_value",
       "name": "X",
       "shadow": {
        "type": "math_slider",
        "fields": {
         "NUM": "0"
        },
        "min": 0,
        "max": 127
       }
      },
      {
       "type": "input_value",
       "name": "Y",
       "shadow": {
        "type": "math_slider",
        "fields": {
         "NUM": "0"
        },
        "min": 0,
        "max": 63
       }
      },
      {
       "type": "input_value",
       "name": "RADIUS",
       "shadow": {
        "type": "math_number",
        "fields": {
         "NUM": "20"
        }
       }
      }
     ]
    }
   ]
  },
  {
   "type": "dev-DFRobot-handpyEsp32@0.0.6_esp32_rectangleDrawing",
   "cat": "handpy_screen",
   "colour": "#87C482",
   "icon": true,
   "shape": "stack",
   "lines": [
    {
     "text": "画矩形 %1 起点 x: %2 y: %3 宽: %4 高: %5",
     "args": [
      {
       "type": "field_dropdown",
       "name": "FILL",
       "options": [
        [
         "填充",
         "FILL"
        ],
        [
         "不填充",
         "NOTFILL"
        ]
       ],
       "value": "FILL"
      },
      {
       "type": "input_value",
       "name": "X",
       "shadow": {
        "type": "math_slider",
        "fields": {
         "NUM": "0"
        },
        "min": 0,
        "max": 127
       }
      },
      {
       "type": "input_value",
       "name": "Y",
       "shadow": {
        "type": "math_slider",
        "fields": {
         "NUM": "0"
        },
        "min": 0,
        "max": 63
       }
      },
      {
       "type": "input_value",
       "name": "WIDTH",
       "shadow": {
        "type": "math_slider",
        "fields": {
         "NUM": "0"
        },
        "min": 0,
        "max": 128
       }
      },
      {
       "type": "input_value",
       "name": "HEIGHT",
       "shadow": {
        "type": "math_slider",
        "fields": {
         "NUM": "0"
        },
        "min": 0,
        "max": 64
       }
      }
     ]
    }
   ]
  },
  {
   "type": "dev-DFRobot-handpyEsp32@0.0.6_esp32_setLightsColor",
   "cat": "handpy_led",
   "colour": "#87C482",
   "icon": true,
   "shape": "stack",
   "lines": [
    {
     "text": "灯号 %1 显示颜色 %2",
     "args": [
      {
       "type": "input_value",
       "name": "LIGHTS",
       "shadow": {
        "type": "dev-DFRobot-handpyEsp32@0.0.6_menu_lightsMenu",
        "fields": {
         "lightsMenu": "0"
        }
       }
      },
      {
       "type": "input_value",
       "name": "COLOR",
       "shadow": {
        "type": "colour_palette",
        "fields": {
         "COLOUR": "#0000ff"
        }
       }
      }
     ]
    }
   ]
  },
  {
   "type": "dev-DFRobot-handpyEsp32@0.0.6_esp32_rgbDisable",
   "cat": "handpy_led",
   "colour": "#87C482",
   "icon": true,
   "shape": "stack",
   "lines": [
    {
     "text": "关闭 %1 LED",
     "args": [
      {
       "type": "field_dropdown",
       "name": "INDEX",
       "options": [
        [
         "全部",
         "all"
        ],
        [
         "0号",
         "No.0"
        ],
        [
         "1号",
         "No.1"
        ],
        [
         "2号",
         "No.2"
        ]
       ],
       "value": "all"
      }
     ]
    }
   ]
  },
  {
   "type": "dev-DFRobot-handpyEsp32@0.0.6_esp32_rgbToColor",
   "cat": "handpy_led",
   "colour": "#87C482",
   "icon": true,
   "shape": "reporter",
   "lines": [
    {
     "text": "红 %1 绿 %2 蓝 %3",
     "args": [
      {
       "type": "input_value",
       "name": "RED",
       "shadow": {
        "type": "math_slider",
        "fields": {
         "NUM": "255"
        },
        "min": 0,
        "max": 255
       }
      },
      {
       "type": "input_value",
       "name": "GREEN",
       "shadow": {
        "type": "math_slider",
        "fields": {
         "NUM": "255"
        },
        "min": 0,
        "max": 255
       }
      },
      {
       "type": "input_value",
       "name": "BLUE",
       "shadow": {
        "type": "math_slider",
        "fields": {
         "NUM": "255"
        },
        "min": 0,
        "max": 255
       }
      }
     ]
    }
   ]
  },
  {
   "type": "dev-DFRobot-handpyEsp32@0.0.6_esp32_setBrightness",
   "cat": "handpy_led",
   "colour": "#87C482",
   "icon": true,
   "shape": "stack",
   "lines": [
    {
     "text": "设置LED灯亮度为 %1",
     "args": [
      {
       "type": "input_value",
       "name": "BRIGHTVALUE",
       "shadow": {
        "type": "dev-DFRobot-handpyEsp32@0.0.6_menu_brightnessMenu",
        "fields": {
         "brightnessMenu": "9"
        }
       }
      }
     ]
    }
   ]
  },
  {
   "type": "dev-DFRobot-handpyEsp32@0.0.6_esp32_getBrightness",
   "cat": "handpy_led",
   "colour": "#87C482",
   "icon": true,
   "shape": "reporter",
   "lines": [
    {
     "text": "读取LED灯亮度",
     "args": []
    }
   ]
  },
  {
   "type": "dev-DFRobot-handpyEsp32@0.0.6_esp32_playMusic",
   "cat": "handpy_music",
   "colour": "#87C482",
   "icon": true,
   "shape": "stack",
   "lines": [
    {
     "text": "播放音乐 %1 重复 %2",
     "args": [
      {
       "type": "field_dropdown",
       "name": "MUSIC",
       "options": [
        [
         "DADADADUM",
         "DADADADUM"
        ],
        [
         "ENTERTAINER",
         "ENTERTAINER"
        ],
        [
         "PRELUDE",
         "PRELUDE"
        ],
        [
         "ODE",
         "ODE"
        ],
        [
         "NYAN",
         "NYAN"
        ],
        [
         "RINGTONE",
         "RINGTONE"
        ],
        [
         "FUNK",
         "FUNK"
        ],
        [
         "BLUES",
         "BLUES"
        ],
        [
         "BIRTHDAY",
         "BIRTHDAY"
        ],
        [
         "WEDDING",
         "WEDDING"
        ],
        [
         "FUNERAL",
         "FUNERAL"
        ],
        [
         "PUNCHLINE",
         "PUNCHLINE"
        ],
        [
         "BADDY",
         "BADDY"
        ],
        [
         "CHASE",
         "CHASE"
        ],
        [
         "BA_DING",
         "BA_DING"
        ],
        [
         "WAWAWAWAA",
         "WAWAWAWAA"
        ],
        [
         "JUMP_UP",
         "JUMP_UP"
        ],
        [
         "JUMP_DOWN",
         "JUMP_DOWN"
        ],
        [
         "POWER_UP",
         "POWER_UP"
        ],
        [
         "POWER_DOWN",
         "POWER_DOWN"
        ]
       ],
       "value": "DADADADUM"
      },
      {
       "type": "field_dropdown",
       "name": "REPEAT",
       "options": [
        [
         "播放一次",
         "Once"
        ],
        [
         "无限循环",
         "Forever"
        ],
        [
         "在后台播放一次",
         "OnceInBackground"
        ],
        [
         "在后台无限循环",
         "ForeverInBackground"
        ]
       ],
       "value": "Once"
      }
     ]
    }
   ]
  },
  {
   "type": "dev-DFRobot-handpyEsp32@0.0.6_esp32_musicSetTempo",
   "cat": "handpy_music",
   "colour": "#87C482",
   "icon": true,
   "shape": "stack",
   "lines": [
    {
     "text": "设置每一拍等同 %1 分音符，每分钟节拍数 %2",
     "args": [
      {
       "type": "input_value",
       "name": "TICKS",
       "shadow": {
        "type": "math_number",
        "fields": {
         "NUM": "4"
        }
       }
      },
      {
       "type": "input_value",
       "name": "TEMPO",
       "shadow": {
        "type": "math_number",
        "fields": {
         "NUM": "60"
        }
       }
      }
     ]
    }
   ]
  },
  {
   "type": "dev-DFRobot-handpyEsp32@0.0.6_esp32_setBuzzerFreq",
   "cat": "handpy_music",
   "colour": "#87C482",
   "icon": true,
   "shape": "stack",
   "lines": [
    {
     "text": "播放音符 %1 %2 拍",
     "args": [
      {
       "type": "input_value",
       "name": "NOTE",
       "shadow": {
        "type": "piano",
        "fields": {
         "PIANO": "131"
        }
       }
      },
      {
       "type": "input_value",
       "name": "BEAT",
       "shadow": {
        "type": "dev-DFRobot-handpyEsp32@0.0.6_menu_beatMenu",
        "fields": {
         "beatMenu": "1"
        }
       }
      }
     ]
    }
   ]
  },
  {
   "type": "dev-DFRobot-handpyEsp32@0.0.6_esp32_backgroundSetBuzzerFreq",
   "cat": "handpy_music",
   "colour": "#87C482",
   "icon": true,
   "shape": "stack",
   "lines": [
    {
     "text": "后台播放音符 %1",
     "args": [
      {
       "type": "input_value",
       "name": "NOTE",
       "shadow": {
        "type": "piano",
        "fields": {
         "PIANO": "131"
        }
       }
      }
     ]
    }
   ]
  },
  {
   "type": "dev-DFRobot-handpyEsp32@0.0.6_esp32_buzzerSwitch",
   "cat": "handpy_music",
   "colour": "#87C482",
   "icon": true,
   "shape": "stack",
   "lines": [
    {
     "text": "停止后台播放",
     "args": []
    }
   ]
  },
  {
   "type": "dev-DFRobot-handpyEsp32@0.0.6_esp32_buzzerRedirect",
   "cat": "handpy_music",
   "colour": "#87C482",
   "icon": true,
   "shape": "stack",
   "lines": [
    {
     "text": "蜂鸣器重定向到引脚 %1",
     "args": [
      {
       "type": "field_dropdown",
       "name": "PIN",
       "options": [
        [
         "P0",
         "P0"
        ],
        [
         "P1",
         "P1"
        ],
        [
         "P7",
         "P7"
        ],
        [
         "P8",
         "P8"
        ],
        [
         "P11",
         "P11"
        ],
        [
         "P13",
         "P13"
        ],
        [
         "P14",
         "P14"
        ],
        [
         "P15",
         "P15"
        ],
        [
         "P16",
         "P16"
        ],
        [
         "P19",
         "P19"
        ],
        [
         "P20",
         "P20"
        ],
        [
         "P",
         "27"
        ],
        [
         "Y",
         "14"
        ],
        [
         "T",
         "12"
        ],
        [
         "H",
         "13"
        ],
        [
         "O",
         "15"
        ],
        [
         "N",
         "4"
        ]
       ],
       "value": "P0"
      }
     ]
    }
   ]
  },
  {
   "type": "ext-DFRobot-MQTT@0.0.4_mqtt_argumentReporterStringNumber_eventHeadInner_",
   "cat": "mqtt",
   "colour": "#8B92E8",
   "icon": false,
   "shape": "reporter",
   "lines": [
    {
     "text": "MQTT消息",
     "args": []
    }
   ]
  },
  {
   "type": "ext-DFRobot-MQTT@0.0.4_mqtt_initial",
   "cat": "mqtt",
   "colour": "#8B92E8",
   "icon": true,
   "shape": "stack",
   "lines": [
    {
     "text": "MQTT 初始化参数 %1",
     "args": [
      {
       "type": "input_value",
       "name": "SETTING",
       "shadow": {
        "type": "mqtt_settings",
        "fields": {
         "MQTT": "{\"platform\": \"easyiot\", \"params\": {\"server1\": \"cn-shanghai\", \"server2\": \"mqtt.heclouds.com\", \"server5\": \"183.230.40.96\", \"server3\": \"iot.dfrobot.com.cn\", \"topic\": \"\"}}"
        }
       }
      }
     ]
    }
   ]
  },
  {
   "type": "ext-DFRobot-MQTT@0.0.4_mqtt_mqttConnect",
   "cat": "mqtt",
   "colour": "#8B92E8",
   "icon": true,
   "shape": "stack",
   "lines": [
    {
     "text": "MQTT 发起连接",
     "args": []
    }
   ]
  },
  {
   "type": "ext-DFRobot-MQTT@0.0.4_mqtt_mqttDisconnect",
   "cat": "mqtt",
   "colour": "#8B92E8",
   "icon": true,
   "shape": "stack",
   "lines": [
    {
     "text": "MQTT 断开连接",
     "args": []
    }
   ]
  },
  {
   "type": "ext-DFRobot-MQTT@0.0.4_mqtt_mqttConnected",
   "cat": "mqtt",
   "colour": "#8B92E8",
   "icon": true,
   "shape": "boolean",
   "lines": [
    {
     "text": "MQTT 连接成功？",
     "args": []
    }
   ]
  },
  {
   "type": "ext-DFRobot-MQTT@0.0.4_mqtt_mqttSend",
   "cat": "mqtt",
   "colour": "#8B92E8",
   "icon": true,
   "shape": "stack",
   "lines": [
    {
     "text": "MQTT 发送消息 %1 至 %2",
     "args": [
      {
       "type": "input_value",
       "name": "MSG",
       "shadow": {
        "type": "text",
        "fields": {
         "TEXT": "hello"
        },
        "quote": true
       }
      },
      {
       "type": "field_dropdown",
       "name": "TOPIC",
       "options": [
        [
         "Topic_0",
         "Topic_0"
        ],
        [
         "Topic_1",
         "Topic_1"
        ],
        [
         "Topic_2",
         "Topic_2"
        ],
        [
         "Topic_3",
         "Topic_3"
        ],
        [
         "Topic_4",
         "Topic_4"
        ]
       ],
       "value": "Topic_0"
      }
     ]
    }
   ]
  },
  {
   "type": "ext-DFRobot-MQTT@0.0.4_mqtt_mqttSend_save",
   "cat": "mqtt",
   "colour": "#8B92E8",
   "icon": true,
   "shape": "stack",
   "lines": [
    {
     "text": "MQTT 发送消息 %1 至 %2 并存数据库 (SIoT V2)",
     "args": [
      {
       "type": "input_value",
       "name": "MSG",
       "shadow": {
        "type": "text",
        "fields": {
         "TEXT": "hello"
        },
        "quote": true
       }
      },
      {
       "type": "field_dropdown",
       "name": "TOPIC",
       "options": [
        [
         "Topic_0",
         "Topic_0"
        ],
        [
         "Topic_1",
         "Topic_1"
        ],
        [
         "Topic_2",
         "Topic_2"
        ],
        [
         "Topic_3",
         "Topic_3"
        ],
        [
         "Topic_4",
         "Topic_4"
        ]
       ],
       "value": "Topic_0"
      }
     ]
    }
   ]
  },
  {
   "type": "ext-DFRobot-MQTT@0.0.4_mqtt_whenReceivedData",
   "cat": "mqtt",
   "colour": "#8B92E8",
   "icon": true,
   "shape": "hat",
   "lines": [
    {
     "text": "当接收到 %1 的 %2",
     "args": [
      {
       "type": "field_dropdown",
       "name": "TOPIC",
       "options": [
        [
         "Topic_0",
         "Topic_0"
        ],
        [
         "Topic_1",
         "Topic_1"
        ],
        [
         "Topic_2",
         "Topic_2"
        ],
        [
         "Topic_3",
         "Topic_3"
        ],
        [
         "Topic_4",
         "Topic_4"
        ]
       ],
       "value": "Topic_0"
      },
      {
       "type": "input_value",
       "name": "MSG",
       "shadow": {
        "type": "ext-DFRobot-MQTT@0.0.4_mqtt_argumentReporterStringNumber_eventHeadInner_",
        "fields": {},
        "label": "MQTT消息"
       }
      }
     ]
    }
   ]
  },
  {
   "type": "ext-DFRobot-MQTT@0.0.4_mqtt_acceptance_meaage",
   "cat": "mqtt",
   "colour": "#8B92E8",
   "icon": true,
   "shape": "hat",
   "lines": [
    {
     "text": "当接收到 %1 的消息为 %2",
     "args": [
      {
       "type": "field_dropdown",
       "name": "TOPIC",
       "options": [
        [
         "Topic_0",
         "Topic_0"
        ],
        [
         "Topic_1",
         "Topic_1"
        ],
        [
         "Topic_2",
         "Topic_2"
        ],
        [
         "Topic_3",
         "Topic_3"
        ],
        [
         "Topic_4",
         "Topic_4"
        ]
       ],
       "value": "Topic_0"
      },
      {
       "type": "input_value",
       "name": "MSG",
       "shadow": {
        "type": "text",
        "fields": {
         "TEXT": "hello"
        },
        "quote": true
       }
      }
     ]
    }
   ]
  },
  {
   "type": "ext-DFRobot-MQTT@0.0.4_mqtt_subscribe",
   "cat": "mqtt",
   "colour": "#8B92E8",
   "icon": true,
   "shape": "stack",
   "lines": [
    {
     "text": "MQTT 订阅 %1",
     "args": [
      {
       "type": "input_value",
       "name": "TOPIC",
       "shadow": {
        "type": "text",
        "fields": {
         "TEXT": "topic/a"
        },
        "quote": true
       }
      }
     ]
    }
   ]
  },
  {
   "type": "ext-DFRobot-MQTT@0.0.4_mqtt_mqttSendToTopic",
   "cat": "mqtt",
   "colour": "#8B92E8",
   "icon": true,
   "shape": "stack",
   "lines": [
    {
     "text": "MQTT 发送消息 %1 至 %2",
     "args": [
      {
       "type": "input_value",
       "name": "MSG",
       "shadow": {
        "type": "text",
        "fields": {
         "TEXT": "hello"
        },
        "quote": true
       }
      },
      {
       "type": "input_value",
       "name": "TOPIC",
       "shadow": {
        "type": "text",
        "fields": {
         "TEXT": "topic/a"
        },
        "quote": true
       }
      }
     ]
    }
   ]
  },
  {
   "type": "ext-DFRobot-MQTT@0.0.4_mqtt_mqttSendToTopic_save",
   "cat": "mqtt",
   "colour": "#8B92E8",
   "icon": true,
   "shape": "stack",
   "lines": [
    {
     "text": "MQTT 发送消息 %1 至 %2 并存数据库 (SIoT V2)",
     "args": [
      {
       "type": "input_value",
       "name": "MSG",
       "shadow": {
        "type": "text",
        "fields": {
         "TEXT": "hello"
        },
        "quote": true
       }
      },
      {
       "type": "input_value",
       "name": "TOPIC",
       "shadow": {
        "type": "text",
        "fields": {
         "TEXT": "topic/a"
        },
        "quote": true
       }
      }
     ]
    }
   ]
  },
  {
   "type": "ext-DFRobot-MQTT@0.0.4_mqtt_whenReceivedTopicData",
   "cat": "mqtt",
   "colour": "#8B92E8",
   "icon": true,
   "shape": "hat",
   "lines": [
    {
     "text": "当接收到 %1 的 %2",
     "args": [
      {
       "type": "input_value",
       "name": "TOPIC",
       "shadow": {
        "type": "text",
        "fields": {
         "TEXT": "topic/a"
        },
        "quote": true
       }
      },
      {
       "type": "input_value",
       "name": "MSG",
       "shadow": {
        "type": "ext-DFRobot-MQTT@0.0.4_mqtt_argumentReporterStringNumber_eventHeadInner_",
        "fields": {},
        "label": "MQTT消息"
       }
      }
     ]
    }
   ]
  },
  {
   "type": "ext-DFRobot-MQTT@0.0.4_mqtt_acceptance_topic_meaage",
   "cat": "mqtt",
   "colour": "#8B92E8",
   "icon": true,
   "shape": "hat",
   "lines": [
    {
     "text": "当接收到 %1 的消息为 %2",
     "args": [
      {
       "type": "input_value",
       "name": "TOPIC",
       "shadow": {
        "type": "text",
        "fields": {
         "TEXT": "topic/a"
        },
        "quote": true
       }
      },
      {
       "type": "input_value",
       "name": "MSG",
       "shadow": {
        "type": "text",
        "fields": {
         "TEXT": "hello"
        },
        "quote": true
       }
      }
     ]
    }
   ]
  },
  {
   "type": "ext-DFRobot-WIFI@0.0.5_wifi_whenDebugInfoDebug_eventHeadInner_",
   "cat": "wifi",
   "colour": "#8B92E8",
   "icon": false,
   "shape": "reporter",
   "lines": [
    {
     "text": "调试信息",
     "args": []
    }
   ]
  },
  {
   "type": "ext-DFRobot-WIFI@0.0.5_wifi_whenDebugInfoError_eventHeadInner_",
   "cat": "wifi",
   "colour": "#8B92E8",
   "icon": false,
   "shape": "reporter",
   "lines": [
    {
     "text": "错误码",
     "args": []
    }
   ]
  },
  {
   "type": "ext-DFRobot-WIFI@0.0.5_wifiConnectByPwd",
   "cat": "wifi",
   "colour": "#8B92E8",
   "icon": true,
   "shape": "stack",
   "lines": [
    {
     "text": "Wi-Fi 连接到 热点: %1 密码: %2",
     "args": [
      {
       "type": "input_value",
       "name": "ACCOUNT",
       "shadow": {
        "type": "text",
        "fields": {
         "TEXT": "yourSSID"
        },
        "quote": true
       }
      },
      {
       "type": "input_value",
       "name": "PASSWORD",
       "shadow": {
        "type": "text",
        "fields": {
         "TEXT": "yourPASSWD"
        },
        "quote": true
       }
      }
     ]
    }
   ]
  },
  {
   "type": "ext-DFRobot-WIFI@0.0.5_wifiConnected",
   "cat": "wifi",
   "colour": "#8B92E8",
   "icon": true,
   "shape": "boolean",
   "lines": [
    {
     "text": "Wi-Fi 连接成功？",
     "args": []
    }
   ]
  },
  {
   "type": "ext-DFRobot-WIFI@0.0.5_wifiDisConnected",
   "cat": "wifi",
   "colour": "#8B92E8",
   "icon": true,
   "shape": "stack",
   "lines": [
    {
     "text": "Wi-Fi 断开连接",
     "args": []
    }
   ]
  },
  {
   "type": "ext-DFRobot-WIFI@0.0.5_wifiGetLocalIP",
   "cat": "wifi",
   "colour": "#8B92E8",
   "icon": true,
   "shape": "reporter",
   "lines": [
    {
     "text": "获取Wi-Fi配置 %1",
     "args": [
      {
       "type": "field_dropdown",
       "name": "CONFIGURATION",
       "options": [
        [
         "IP 地址",
         "getWiFiLocalIP"
        ],
        [
         "子网掩码",
         "getWiFiNetmask"
        ],
        [
         "网关",
         "getGateway"
        ],
        [
         "DNS",
         "getDNS"
        ]
       ],
       "value": "getWiFiLocalIP"
      }
     ]
    }
   ]
  },
  {
   "type": "ext-DFRobot-WIFI@0.0.5_wifiSetSoftAP",
   "cat": "wifi",
   "colour": "#8B92E8",
   "icon": true,
   "shape": "stack",
   "lines": [
    {
     "text": "Wi-Fi 设置AP模式: %1 密码: %2",
     "args": [
      {
       "type": "input_value",
       "name": "ACCOUNT2",
       "shadow": {
        "type": "text",
        "fields": {
         "TEXT": "yourSSID"
        },
        "quote": true
       }
      },
      {
       "type": "input_value",
       "name": "PASSWORD2",
       "shadow": {
        "type": "text",
        "fields": {
         "TEXT": "yourPASSWD"
        },
        "quote": true
       }
      }
     ]
    }
   ]
  },
  {
   "type": "ext-DFRobot-WIFI@0.0.5_wifiGetSoftAPIP",
   "cat": "wifi",
   "colour": "#8B92E8",
   "icon": true,
   "shape": "reporter",
   "lines": [
    {
     "text": "Wi-Fi 获取AP模式本机IP",
     "args": []
    }
   ]
  },
  {
   "type": "ext-DFRobot-WIFI@0.0.5_whenDebugInfo",
   "cat": "wifi",
   "colour": "#8B92E8",
   "icon": true,
   "shape": "hat",
   "lines": [
    {
     "text": "Wi-Fi 获取 %1 %2",
     "args": [
      {
       "type": "input_value",
       "name": "DEBUG",
       "shadow": {
        "type": "ext-DFRobot-WIFI@0.0.5_wifi_whenDebugInfoDebug_eventHeadInner_",
        "fields": {},
        "label": "调试信息"
       }
      },
      {
       "type": "input_value",
       "name": "ERROR",
       "shadow": {
        "type": "ext-DFRobot-WIFI@0.0.5_wifi_whenDebugInfoError_eventHeadInner_",
        "fields": {},
        "label": "错误码"
       }
      }
     ]
    }
   ]
  }
 ],
 "menus": {
  "dev-DFRobot-handpyEsp32@0.0.6_menu_oledLineMenu": {
   "field": "oledLineMenu",
   "options": [
    [
     "1",
     "1"
    ],
    [
     "2",
     "2"
    ],
    [
     "3",
     "3"
    ],
    [
     "4",
     "4"
    ]
   ]
  },
  "dev-DFRobot-handpyEsp32@0.0.6_menu_lightsMenu": {
   "field": "lightsMenu",
   "options": [
    [
     "0",
     "0"
    ],
    [
     "1",
     "1"
    ],
    [
     "2",
     "2"
    ],
    [
     "全部(-1)",
     "ALL"
    ]
   ]
  },
  "dev-DFRobot-handpyEsp32@0.0.6_menu_brightnessMenu": {
   "field": "brightnessMenu",
   "options": [
    [
     "9",
     "9"
    ],
    [
     "8",
     "8"
    ],
    [
     "7",
     "7"
    ],
    [
     "6",
     "6"
    ],
    [
     "5",
     "5"
    ],
    [
     "4",
     "4"
    ],
    [
     "3",
     "3"
    ],
    [
     "2",
     "2"
    ],
    [
     "1",
     "1"
    ],
    [
     "0",
     "0"
    ]
   ]
  },
  "dev-DFRobot-handpyEsp32@0.0.6_menu_beatMenu": {
   "field": "beatMenu",
   "options": [
    [
     "1/4",
     "1/4"
    ],
    [
     "1/2",
     "1/2"
    ],
    [
     "3/4",
     "3/4"
    ],
    [
     "1",
     "1"
    ],
    [
     "1.5",
     "1.5"
    ],
    [
     "2",
     "2"
    ],
    [
     "3",
     "3"
    ],
    [
     "4",
     "4"
    ]
   ]
  }
 }
};
